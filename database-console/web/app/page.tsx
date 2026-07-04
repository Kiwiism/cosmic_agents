"use client";

import {
  Activity, BookOpen, Boxes, ChevronLeft, ChevronRight, CircleUserRound, Coins,
  Database, ExternalLink, MapPinned, PackagePlus, PackageSearch, RefreshCw, Search, Server,
  ShieldCheck, ShoppingBasket, Skull, Store, Ticket, Trash2, UsersRound, X
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ConsoleConnectionError } from "../components/auth/ConsoleAuth";
import { ConsoleNavItem, ConsoleShell } from "../components/layout/ConsoleShell";
import { PageToolbar } from "../components/ui/PageToolbar";
import { api, assetUrl, assetUrls } from "../lib/api";

type View = "dashboard"|"items"|"mobs"|"maps"|"npcs"|"skills"|"shops"|"gacha"|"accounts"|"inventory"|"audit";
type Entity = {
  entity_type:string; entity_id:number; name:string; description?:string; category?:string;
  subtype?:string; level_value?:number; job_id?:number; job_name?:string; used_in_game?:boolean;
  source_path?:string; properties_json?:Record<string,any>|string;location_name?:string;
};
type Page<T>={items:T[];page:number;size:number;total:number;pages:number};
type Jump={view:View;id?:number;type?:string};
type HistoryEntry={type:string;id:number;name?:string};
type Notify=(message:string)=>void;

const nav:readonly ConsoleNavItem<View>[] = [
  {key:"dashboard",label:"Dashboard",icon:Activity},{key:"items",label:"Items",icon:PackageSearch},{key:"mobs",label:"Mobs",icon:Skull},
  {key:"maps",label:"Maps",icon:MapPinned},{key:"npcs",label:"NPCs",icon:UsersRound},{key:"skills",label:"Skills",icon:BookOpen},
  {key:"shops",label:"Shops",icon:ShoppingBasket},{key:"gacha",label:"Gachapon",icon:Ticket},{key:"accounts",label:"Accounts",icon:UsersRound},
  {key:"inventory",label:"Inventory & storage",icon:Boxes},{key:"audit",label:"Audit",icon:ShieldCheck},
];
const catalogItemTypes=["EQUIP","FACE","HAIR","CONSUME","SETUP","ETC","CASH"];
const inventoryItemTypes=["EQUIP","CONSUME","SETUP","ETC","CASH"];
const itemCategories:Record<string,string[]>={
  EQUIP:["All Weapons","All Armors","Hat","Accessory","Top","Overall","Bottom","Shoes","Gloves","Shield","Cape","Ring","Pet Equip","Mount","Dragon Equip",
    "One-Handed Sword","One-Handed Axe","One-Handed Mace","Dagger","Wand","Staff","Two-Handed Sword","Two-Handed Axe","Two-Handed Mace",
    "Spear","Polearm","Bow","Crossbow","Claw","Knuckle","Gun","Cash Weapon","Skill Effect","Unused Weapon",
    "Cash Hat","Cash Accessory","Cash Top","Cash Overall","Cash Bottom","Cash Shoes","Cash Gloves","Cash Shield","Cash Cape","Cash Ring","Cash Pet Equip","Cash Mount"],
  FACE:["Face","Cash Face"],HAIR:["Hair","Cash Hair"],
  CONSUME:["Potion","Food","Return Scroll","Equipment Scroll","Skill Book","Status Cure","Arrow","Throwing Star","Bullet","Transformation","Other Consume"],
  SETUP:["Chair","Event Setup","Other Setup"],ETC:["Monster Drop","Ore","Plate / Jewel","Quest Item","Skill Book","Book","Certificate","Other Etc"],
  CASH:["Pet","Package","Effect","Store Permit","Teleport","Character Reset","Megaphone","Message","Messenger","Note","Music","Weather","Character","Safety Charm","Shop","Beauty","Emotion","Pet Consumable","Pet Name","Currency","EXP Coupon","Gachapon","Item Search","Wedding","Map Effect","Morph","Drop Coupon","Chalkboard","Other Cash"]
};

export default function App(){
  const [view,setView]=useState<View>("dashboard");
  const [apiReady,setApiReady]=useState<boolean|null>(null);
  const [startupError,setStartupError]=useState("");
  const [notice,setNotice]=useState("");
  const [drawer,setDrawer]=useState<{type:string;id:number;context?:string}|null>(null);
  const [embeddedInspector,setEmbeddedInspector]=useState<"none"|"standard"|"wide">("none");
  const [viewHistory,setViewHistory]=useState<HistoryEntry[]>([]);const [historyIndex,setHistoryIndex]=useState(-1);
  const [focusId,setFocusId]=useState<number>();

  useEffect(()=>{api("/api/setup/status").then(()=>setApiReady(true)).catch(()=>{
    setStartupError("The Console API is offline or its database credentials are not configured.");
    setApiReady(false);
  })},[]);
  function openDrawer(type:string,id:number,context?:string){
    setDrawer({type,id,context});setViewHistory(previous=>{
      const next=[...previous.slice(0,historyIndex+1),{type,id}].slice(-10);setHistoryIndex(next.length-1);return next;
    });
  }
  function jump(target:Jump){setView(target.view);setFocusId(target.id);if(target.type&&target.id)openDrawer(target.type,target.id)}
  function inspectEntity(type:string,id:number){openDrawer(type,id)}
  function moveHistory(index:number){const entry=viewHistory[index];if(!entry)return;setHistoryIndex(index);setDrawer({type:entry.type,id:entry.id})}
  function nameHistory(type:string,id:number,name:string){setViewHistory(previous=>previous.map(entry=>entry.type===type&&entry.id===id?{...entry,name}:entry))}
  if(apiReady===null)return <div className="splash">Preparing Cosmic Database Console...</div>;
  if(startupError)return <ConsoleConnectionError mark="DC" productName="Database Console" message={startupError}/>;
  const inspector=drawer&&<EntityDrawer entity={drawer} close={()=>setDrawer(null)} jump={jump} history={viewHistory} historyIndex={historyIndex} moveHistory={moveHistory} named={nameHistory}/>;
  return <ConsoleShell activeView={view} brandMark="DC" brandSubtitle="Database Console" eyebrow="COSMIC OPERATIONS"
    headerStatus="v83 data explorer" inspectorOpen={Boolean(drawer)||embeddedInspector!=="none"}
    inspectorSize={embeddedInspector==="wide"?"wide":"standard"} navigation={nav}
    onNavigate={next=>{setView(next);setFocusId(undefined);setDrawer(null);setEmbeddedInspector("none")}}
    sidebarStatus={<><Database size={16}/> MySQL connected</>} inspector={inspector}>
      {notice&&<div className="notice" onClick={()=>setNotice("")}>{notice}</div>}
        {view==="dashboard"&&<Dashboard/>}
        {view==="items"&&<Library fixedType="ITEM" onOpen={inspectEntity}/>}
        {view==="mobs"&&<Drops notify={setNotice} focusMob={focusId} onOpen={(type,id)=>openDrawer(type,id,type==="MOB"?"drops":undefined)}/>}
        {view==="maps"&&<Maps focusMap={focusId} onOpen={inspectEntity}/>}
        {view==="npcs"&&<Library fixedType="NPC" onOpen={inspectEntity}/>}
        {view==="skills"&&<Skills focusSkill={focusId} onOpen={inspectEntity}/>}
        {view==="shops"&&<Shops notify={setNotice} focusShop={focusId} onOpen={inspectEntity}/>}
        {view==="gacha"&&<Gachapon notify={setNotice} onOpen={inspectEntity}/>}
        {view==="accounts"&&<Accounts notify={setNotice} onCharacter={id=>jump({view:"inventory",id})}/>}
        {view==="inventory"&&<Inventory notify={setNotice} focusCharacter={focusId} onOpen={inspectEntity} jump={jump} onInspectorChange={setEmbeddedInspector}/>}
        {view==="audit"&&<Audit/>}
  </ConsoleShell>
}

function Dashboard(){
  const [data,setData]=useState<any>();useEffect(()=>{api("/api/dashboard").then(setData)},[]);
  if(!data)return <Loading/>;
  return <><div className={`server-banner ${data.server.status==="UP"?"online":"offline"}`}><div className="pulse"/><div>
    <strong>Game server {data.server.status.toLowerCase()}</strong><span>{data.server.status==="UP"?"Live bridge available":"Database editing remains available"}</span></div><Server/></div>
    <div className="metric-grid">{[
      ["Accounts",data.metrics.accounts,UsersRound],["Characters",data.metrics.characters,CircleUserRound],
      ["Catalog",data.metrics.catalogEntities,PackageSearch],["Drops",data.metrics.drops,Skull],
      ["Shops",data.metrics.shops,Store],["Maps",data.metrics.catalogMaps,MapPinned],
      ["Regions",data.metrics.regions,MapPinned],["Mob spawns",data.metrics.mobSpawnPoints,Skull],
      ["NPC placements",data.metrics.npcPlacements,UsersRound],["Jobs",data.metrics.jobs,ShieldCheck]
    ].map(([label,value,Icon]:any)=><article className="metric" key={label}><Icon size={20}/><p>{label}</p><strong>{Number(value).toLocaleString()}</strong></article>)}</div>
    <div className="two-column"><article className="panel"><PanelTitle title="System readiness" subtitle="Database, WZ and runtime status"/>
      <Status label="Game database" value="Connected"/><Status label="WZ catalog" value={`${data.metrics.catalogEntities} entries`}/>
      <Status label="Live bridge" value={data.server.status}/><Status label="Queued operations" value={String(data.metrics.queuedOperations)}/></article>
      <article className="panel hero-panel"><p className="eyebrow">CATALOG PIPELINE</p><h2>Index names, stats, levels and provenance</h2>
        <p>Reads String.wz, Mob.wz, Character.wz, Item.wz, Skill.wz and Map.wz XML. Existing game database data is not modified.</p><ImportButton/></article></div></>
}

function ImportButton(){
  const [busy,setBusy]=useState(false);const [result,setResult]=useState("");
  async function run(){setBusy(true);try{const r=await api<any>("/api/catalog/import",{method:"POST"});setResult(`${r.entities.toLocaleString()} entities, ${r.files.toLocaleString()} files, ${r.errors} errors | ${r.wzRoot}`)}finally{setBusy(false)}}
  return <div className="inline-actions"><button className="primary" disabled={busy} onClick={run}><RefreshCw className={busy?"spin":""} size={16}/>{busy?"Indexing WZ...":"Import / refresh catalog"}</button>{result&&<small>{result}</small>}</div>
}

function Library({fixedType,onOpen}:{fixedType:"ITEM"|"NPC";onOpen:(type:string,id:number)=>void}){
  const [query,setQuery]=useState("");const type=fixedType;const [subtype,setSubtype]=useState("");const [category,setCategory]=useState("");
  const [usedOnly,setUsedOnly]=useState(false);
  const [minLevel,setMinLevel]=useState("");const [maxLevel,setMaxLevel]=useState("");const [job,setJob]=useState("");
  const [sort,setSort]=useState("name");const [direction,setDirection]=useState("asc");const [page,setPage]=useState(0);
  const [region,setRegion]=useState("");const [regions,setRegions]=useState<any[]>([]);const [jobs,setJobs]=useState<any[]>([]);
  const [data,setData]=useState<Page<Entity>>({items:[],page:0,size:48,total:0,pages:0});
  useEffect(()=>{api<any[]>("/api/catalog/regions").then(setRegions);api<any[]>("/api/catalog/jobs").then(setJobs)},[]);
  useEffect(()=>{const timer=setTimeout(()=>{const p=new URLSearchParams({q:query,type,subtype,category,sort,direction,usedOnly:String(usedOnly),page:String(page),size:"48"});
    if(minLevel)p.set("minLevel",minLevel);if(maxLevel)p.set("maxLevel",maxLevel);if(job)p.set("jobId",job);if(region)p.set("region",region);
    api<Page<Entity>>(`/api/catalog/search?${p}`).then(setData)},180);return()=>clearTimeout(timer)},[query,type,subtype,category,minLevel,maxLevel,job,region,usedOnly,sort,direction,page]);
  useEffect(()=>setPage(0),[query,type,subtype,category,minLevel,maxLevel,job,region,usedOnly,sort,direction]);
  return <><PageToolbar query={query} onQueryChange={setQuery} placeholder="Search every name or ID">
    {type==="ITEM"&&<><select value={subtype} onChange={e=>{setSubtype(e.target.value);setCategory("")}}><option value="">All item types</option>{catalogItemTypes.map(x=><option key={x}>{x}</option>)}</select>
      <select value={category} onChange={e=>setCategory(e.target.value)} disabled={!subtype}><option value="">All {subtype||"subcategories"}</option>{(itemCategories[subtype]||[]).map(x=><option key={x}>{x}</option>)}</select></>}
    <label className="filter-check"><input type="checkbox" checked={usedOnly} onChange={e=>setUsedOnly(e.target.checked)}/>Used in game only</label>
    <select value={sort} onChange={e=>setSort(e.target.value)}><option value="name">Sort: name</option><option value="id">Sort: ID</option><option value="level">Sort: level</option><option value="type">Sort: type</option></select>
    <button className="secondary" onClick={()=>setDirection(direction==="asc"?"desc":"asc")}>{direction==="asc"?"Ascending":"Descending"}</button></PageToolbar>
    <div className="results-bar"><strong>{data.total.toLocaleString()} records</strong><Pager page={data.page} pages={data.pages} setPage={setPage}/></div>
    <div className="entity-grid">{data.items.map(row=><EntityCard key={`${row.entity_type}-${row.entity_id}`} row={row} open={()=>onOpen(row.entity_type,row.entity_id)}/>)}</div>
    <Pager page={data.page} pages={data.pages} setPage={setPage}/>
    </>
}

function EntityCard({row,open}:{row:Entity;open:()=>void}){
  const props=metadata(row.properties_json);
  return <button className="entity-card" onClick={open}>
    <EntityImage type={row.entity_type} id={row.entity_id} properties={props}/><div><div className="tag-row"><span className="tag">{row.entity_type}</span>
      {row.subtype&&<span className="tag soft">{row.subtype}</span>}{row.category&&<span className="tag soft">{row.category}</span>}{row.used_in_game&&<span className="tag used">Used</span>}</div>
      <h3>{row.entity_type==="NPC"&&row.location_name?`${row.name}: ${row.location_name}`:row.name}</h3><code>{row.entity_id}</code>{row.level_value!=null&&<span className="micro">Lv. {row.level_value}</span>}
      {row.job_id!=null&&<span className="micro">{row.job_name||"Job"} ({row.job_id})</span>}
      <p>{row.description||summaryFromProps(props)||"No description in String.wz"}</p>
      {props.statRanges&&<StatStrip ranges={props.statRanges}/>}<small className="source-line">{row.source_path}</small></div><ExternalLink className="card-link" size={14}/>
  </button>
}

function Maps({focusMap,onOpen}:{focusMap?:number;onOpen:(type:string,id:number)=>void}){
  const [regions,setRegions]=useState<any[]>([]);const [region,setRegion]=useState<any>();const [maps,setMaps]=useState<any[]>([]);const [query,setQuery]=useState("");const [page,setPage]=useState(0);const [mapSearch,setMapSearch]=useState<Entity|null>(null);
  useEffect(()=>{api<any[]>("/api/catalog/regions").then(setRegions)},[]);
  useEffect(()=>{if(focusMap)onOpen("MAP",focusMap)},[focusMap]);
  async function choose(value:any){setRegion(value);setPage(0);setMaps(await api<any[]>(`/api/catalog/regions/${value.region_code}/maps`))}
  const filtered=maps.filter(m=>!query||m.name?.toLowerCase().includes(query.toLowerCase())||String(m.entity_id).includes(query));
  return <><div className="top-search"><Autocomplete type="MAP" value={mapSearch} onSelect={value=>{setMapSearch(value);onOpen("MAP",value.entity_id)}} placeholder="Search any map by name or ID"/></div>
    <div className="region-grid explorer-regions large">{regions.map(r=><button className={region?.region_code===r.region_code?"selected":""} key={r.region_code} onClick={()=>choose(r)}>
    <img src={assetUrl("MAP",r.representative_map_id)} alt="" onError={hideImage}/><strong>{r.region_name}</strong><small>{r.map_count} maps | {r.mob_count} mobs</small></button>)}</div>
    {region&&<article className="panel catalog-section"><div className="catalog-head"><PanelTitle title={`${region.region_name} maps`} subtitle="Portals, monsters, NPCs and WZ map metadata"/><SearchInput value={query} setValue={v=>{setQuery(v);setPage(0)}} placeholder="Search map name or ID"/><Pager page={page} pages={Math.ceil(filtered.length/30)} setPage={setPage}/></div>
      <div className="map-grid">{filtered.slice(page*30,page*30+30).map(m=><button className={m.is_town?"town-map":""} key={m.entity_id} onClick={()=>onOpen("MAP",m.entity_id)}><img src={assetUrl("MAP",m.entity_id)} alt="" onError={hideImage}/><span>{m.is_town&&<span className="tag used">Town</span>}<strong>{m.name||m.entity_id}</strong><code>{m.entity_id}</code><small>{m.mob_count} mobs | {m.npc_count} NPCs | {m.spawn_count} spawns</small></span></button>)}</div><Pager page={page} pages={Math.ceil(filtered.length/30)} setPage={setPage}/></article>}</>
}

function Skills({focusSkill,onOpen}:{focusSkill?:number;onOpen:(type:string,id:number)=>void}){
  const [jobs,setJobs]=useState<any[]>([]);const [job,setJob]=useState<any>();const [data,setData]=useState<Page<Entity>>({items:[],page:0,size:48,total:0,pages:0});const [query,setQuery]=useState("");const [page,setPage]=useState(0);
  useEffect(()=>{api<any[]>("/api/catalog/jobs").then(setJobs)},[]);useEffect(()=>{if(focusSkill)onOpen("SKILL",focusSkill)},[focusSkill]);
  useEffect(()=>{if(!job&&!query)return;const p=new URLSearchParams({type:"SKILL",q:query,page:String(page),size:"48",sort:"name"});if(job)p.set("jobId",String(job.job_id));api<Page<Entity>>(`/api/catalog/search?${p}`).then(setData)},[job,query,page]);
  return <><div className="top-search"><SearchInput value={query} setValue={v=>{setQuery(v);setPage(0)}} placeholder="Search any skill name or ID"/></div><div className="explorer-split"><div className="list-column"><div className="list-panel job-list">{jobs.map(j=><button className={job?.job_id===j.job_id?"selected":""} key={j.job_id} onClick={()=>{setJob(j);setQuery("");setPage(0)}}><JobBadge jobId={j.job_id}/><span><strong>{j.job_name}</strong><small>Job {j.job_id}</small></span><ChevronRight/></button>)}</div></div>
    <article className="panel"><div className="catalog-head"><PanelTitle title={job?`${job.job_name} skills`:"Choose a job"} subtitle={job?`Job ${job.job_id} skill catalog and per-level details`:"Browse skills by their owning job"}/>{job&&<><SearchInput value={query} setValue={v=>{setQuery(v);setPage(0)}} placeholder="Search skill name or ID"/><Pager page={page} pages={data.pages} setPage={setPage}/></>}</div>
      {job||query?<><div className="entity-grid">{data.items.map(row=><EntityCard key={row.entity_id} row={row} open={()=>onOpen("SKILL",row.entity_id)}/>)}</div><Pager page={page} pages={data.pages} setPage={setPage}/></>:<Empty text="Choose a job or search for a skill"/>}</article></div></>
}

function Drops({notify,focusMob,onOpen}:{notify:Notify;focusMob?:number;onOpen:(type:string,id:number)=>void}){
  const [mode,setMode]=useState<"mob"|"global">("mob");const [mob,setMob]=useState<Entity|null>(null);const [rows,setRows]=useState<any[]>([]);
  const [regions,setRegions]=useState<any[]>([]);const [region,setRegion]=useState<any>();const [regionMobs,setRegionMobs]=useState<any[]>([]);const [mobPage,setMobPage]=useState(0);
  const [minLevel,setMinLevel]=useState("");const [maxLevel,setMaxLevel]=useState("");const [mobSort,setMobSort]=useState("level");const [mobDirection,setMobDirection]=useState<"asc"|"desc">("asc");
  useEffect(()=>{api<any[]>("/api/catalog/regions").then(setRegions)},[]);
  async function chooseRegion(value:any){setRegion(value);setMob(null);setMobPage(0);setRegionMobs(await api<any[]>(`/api/catalog/regions/${value.region_code}/mobs`))}
  useEffect(()=>{if(focusMob)api<any[]>(`/api/catalog/suggest?q=${focusMob}&type=MOB`).then(r=>r[0]&&setMob(r[0]))},[focusMob]);
  async function load(){setRows(await api<any[]>(mode==="global"?"/api/global-drops":`/api/drops?mobId=${mob?.entity_id||0}`))}
  useEffect(()=>{if(mode==="global"||mob)void load()},[mode,mob]);
  async function patch(row:any,field:string,value:number){const body=mode==="global"
    ?{continent:row.continent,itemId:row.itemid,minimumQuantity:field==="minimum_quantity"?value:row.minimum_quantity,maximumQuantity:field==="maximum_quantity"?value:row.maximum_quantity,questId:row.questid,chance:field==="chance"?value:row.chance,comments:row.comments||"",reason:`Inline ${field} update`}
    :{mobId:row.dropperid,itemId:row.itemid,minimumQuantity:field==="minimum_quantity"?value:row.minimum_quantity,maximumQuantity:field==="maximum_quantity"?value:row.maximum_quantity,questId:row.questid,chance:field==="chance"?value:row.chance,reason:`Inline ${field} update`};
    await api(`${mode==="global"?"/api/global-drops":"/api/drops"}/${row.id}`,{method:"PUT",body:JSON.stringify(body)});notify("Drop updated");load()}
  async function remove(row:any){if(!confirm("Delete this drop?"))return;await api(`${mode==="global"?"/api/global-drops":"/api/drops"}/${row.id}?reason=Deleted%20from%20Console`,{method:"DELETE"});load()}
  const visibleMobs=useMemo(()=>regionMobs.filter(m=>(!minLevel||Number(m.level_value)>=Number(minLevel))&&(!maxLevel||Number(m.level_value)<=Number(maxLevel))).sort((a,b)=>{
    const result=mobSort==="name"
      ?String(a.name).localeCompare(String(b.name))
      :mobSort==="spawns"
        ?Number(a.spawn_count)-Number(b.spawn_count)
        :Number(a.level_value)-Number(b.level_value);
    return mobDirection==="asc"?result:-result;
  }),[regionMobs,minLevel,maxLevel,mobSort,mobDirection]);
  return <><div className="tab-bar"><button className={mode==="mob"?"active":""} onClick={()=>setMode("mob")}>Monster explorer</button><button className={mode==="global"?"active":""} onClick={()=>setMode("global")}>Global drops</button></div>
    {mode==="mob"&&<><Autocomplete type="MOB" value={mob} onSelect={value=>{setMob(value);onOpen("MOB",value.entity_id)}} placeholder="Find monster by name or ID"/>
      <div className="region-browser"><div className="region-grid">{regions.map(r=><button className={region?.region_code===r.region_code?"selected":""} key={r.region_code} onClick={()=>chooseRegion(r)}>
        <img src={assetUrl("MAP",r.representative_map_id)} alt="" onError={hideImage}/><strong>{r.region_name}</strong><small>{r.mob_count} mobs | {r.map_count} maps</small></button>)}</div>
      {region&&<div className="mob-picker"><div className="mob-picker-head"><h3>{region.region_name} monsters</h3><div className="compact-filters"><input type="number" min="0" placeholder="Min level" value={minLevel} onChange={e=>{setMinLevel(e.target.value);setMobPage(0)}}/><input type="number" min="0" placeholder="Max level" value={maxLevel} onChange={e=>{setMaxLevel(e.target.value);setMobPage(0)}}/><select value={mobSort} onChange={e=>{setMobSort(e.target.value);setMobPage(0)}}><option value="level">Level</option><option value="name">Name</option><option value="spawns">Spawn count</option></select><button className="secondary" onClick={()=>{setMobDirection(mobDirection==="asc"?"desc":"asc");setMobPage(0)}}>{mobDirection==="asc"?"Ascending":"Descending"}</button></div><Pager page={mobPage} pages={Math.ceil(visibleMobs.length/24)} setPage={setMobPage}/></div>
        <div className="mob-picker-grid">{visibleMobs.slice(mobPage*24,mobPage*24+24).map(m=><button key={m.entity_id} onClick={()=>{setMob({entity_type:"MOB",entity_id:m.entity_id,name:m.name,level_value:m.level_value});onOpen("MOB",m.entity_id)}}>
        <EntityImage type="MOB" id={m.entity_id} properties={metadata(m.properties_json)}/><span><strong>{m.name}</strong><small>Lv. {m.level_value||0} | {m.spawn_count} spawns | {m.map_count} maps</small></span></button>)}</div><Pager page={mobPage} pages={Math.ceil(visibleMobs.length/24)} setPage={setMobPage}/></div>}</div></>}
    <div className="two-column wide-left"><article className="panel"><PanelTitle title={mode==="global"?"Global drops":mob?`${mob.name} drops`:"Choose a monster"} subtitle={`${rows.length} entries. Click numeric values to edit.`}/>
      <div className="rich-list">{rows.map(row=><div className="rich-row" key={row.id}><button className="icon-link" onClick={()=>row.itemid>0&&onOpen("ITEM",row.itemid)}>{row.itemid===0?<Coins/>:<img src={assetUrl("ITEM",row.itemid)} alt=""/>}</button>
        <button className="row-identity" onClick={()=>row.itemid>0&&onOpen("ITEM",row.itemid)}><strong>{row.itemid===0?"Meso drop":row.item_name||`Item ${row.itemid}`}</strong><code>{row.itemid===0?"Currency":row.itemid}</code></button>
        {mode==="global"&&<span className="tag soft">Continent {row.continent}</span>}
        <InlineNumber value={row.minimum_quantity} save={v=>patch(row,"minimum_quantity",v)} label="Min"/>
        <InlineNumber value={row.maximum_quantity} save={v=>patch(row,"maximum_quantity",v)} label="Max"/>
        <InlineNumber value={row.chance} save={v=>patch(row,"chance",v)} label="Chance"/>
        <Chance value={row.chance}/><button className="danger-icon" onClick={()=>remove(row)} title="Delete drop"><Trash2 size={15}/></button></div>)}</div></article>
      <DropAdd mode={mode} mob={mob} after={()=>{notify("Drop added");load()}}/></div></>
}

function DropAdd({mode,mob,after}:{mode:"mob"|"global";mob:Entity|null;after:()=>void}){
  const [item,setItem]=useState<Entity|null>(null);const [chance,setChance]=useState(10000);
  async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();if(!item||mode==="mob"&&!mob)return;const f=new FormData(e.currentTarget);
    const body={mobId:mob?.entity_id,continent:Number(f.get("continent")||-1),itemId:item.entity_id,minimumQuantity:Number(f.get("min")),maximumQuantity:Number(f.get("max")),questId:Number(f.get("quest")),chance,comments:f.get("comments"),reason:String(f.get("reason"))};
    await api(mode==="global"?"/api/global-drops":"/api/drops",{method:"POST",body:JSON.stringify(body)});setItem(null);after()}
  return <form className="panel editor-form" onSubmit={submit}><PanelTitle title="Add drop" subtitle="Search catalog or add the monster's meso drop"/>
    <Autocomplete type="ITEM" value={item?.entity_id===0?null:item} onSelect={setItem} placeholder="Search item name or ID"/><button type="button" className="secondary" onClick={()=>setItem({entity_type:"ITEM",entity_id:0,name:"Meso drop",description:"Currency amount uses minimum and maximum quantity"})}><Coins/>Select meso drop</button>
    {item&&item.entity_id===0?<div className="selected-entity"><Coins/><span><strong>Meso drop</strong><small>Minimum and maximum are meso amounts</small></span></div>:item&&<SelectedEntity entity={item}/>}
    {mode==="global"&&<label>Continent<input name="continent" type="number" defaultValue="-1"/></label>}
    <div className="form-row"><label>Minimum<input name="min" type="number" defaultValue="1"/></label><label>Maximum<input name="max" type="number" defaultValue="1"/></label></div>
    <label>Chance<input value={chance} onChange={e=>setChance(Number(e.target.value))} type="number" required/></label><Chance value={chance}/>
    <label>Quest ID<input name="quest" type="number" defaultValue="0"/></label>{mode==="global"&&<label>Comments<input name="comments"/></label>}
    <label>Reason<textarea name="reason" required/></label><button className="primary" disabled={!item}>Add drop</button></form>
}

function Shops({notify,focusShop,onOpen}:{notify:Notify;focusShop?:number;onOpen:(type:string,id:number)=>void}){
  const [query,setQuery]=useState("");const [shops,setShops]=useState<any[]>([]);const [selected,setSelected]=useState<number|undefined>(focusShop);const [items,setItems]=useState<any[]>([]);const [page,setPage]=useState(0);
  const loadShops=()=>api<any[]>(`/api/shops?query=${encodeURIComponent(query)}`).then(setShops);
  const loadItems=async()=>{if(selected)setItems(await api<any[]>(`/api/shops/${selected}/items`))};
  useEffect(()=>{setPage(0);const t=setTimeout(loadShops,180);return()=>clearTimeout(t)},[query]);useEffect(()=>{if(selected)loadItems()},[selected]);
  async function patch(row:any,field:string,value:number){await api(`/api/shops/${selected}/items/${row.shopitemid}`,{method:"PUT",body:JSON.stringify({itemId:row.itemid,price:field==="price"?value:row.price,pitch:row.pitch,position:field==="position"?value:row.position,reason:`Inline ${field} update`})});notify("Shop item updated");loadItems()}
  async function createShop(){const npc=prompt("NPC ID for the new shop");if(!npc)return;const result=await api<any>("/api/shops",{method:"POST",body:JSON.stringify({npcId:Number(npc),reason:"Created in Console"})});await loadShops();setSelected(Number(result.shopId))}
  async function addItem(item:Entity){if(!selected)return;const price=Number(prompt("Price","1"));if(Number.isNaN(price))return;const position=(items.at(-1)?.position||100)+4;await api(`/api/shops/${selected}/items`,{method:"POST",body:JSON.stringify({itemId:item.entity_id,price,pitch:0,position,reason:"Added in Console"})});loadItems()}
  async function remove(row:any){if(!confirm("Delete this shop item?"))return;await api(`/api/shops/${selected}/items/${row.shopitemid}?reason=Deleted%20from%20Console`,{method:"DELETE"});loadItems()}
  async function drag(row:any,target:any){if(row.shopitemid===target.shopitemid)return;await api(`/api/shops/${selected}/items/swap`,{method:"POST",body:JSON.stringify({firstItemId:row.shopitemid,secondItemId:target.shopitemid,reason:"Reordered in Console"})});notify("Shop order updated");await loadItems()}
  return <><PageToolbar query={query} onQueryChange={setQuery} placeholder="Search NPC, shop ID or NPC ID"><button className="primary" onClick={createShop}>New shop</button></PageToolbar>
    <div className="split-list"><div className="list-column"><Pager page={page} pages={Math.ceil(shops.length/12)} setPage={setPage}/><div className="list-panel">{shops.slice(page*12,page*12+12).map(s=><button key={s.shopid} className={selected===s.shopid?"selected":""} onClick={()=>setSelected(s.shopid)}>
      <EntityImage className="list-avatar" type="NPC" id={s.npcid}/><span><strong>{s.primary_map_name?`${s.npc_name||`NPC ${s.npcid}`}: ${s.primary_map_name}`:s.npc_name||`NPC ${s.npcid}`}</strong><small>Shop {s.shopid} | {s.item_count} items</small><small>{s.npcid}</small></span><ChevronRight size={16}/></button>)}</div><Pager page={page} pages={Math.ceil(shops.length/12)} setPage={setPage}/></div>
      <article className="panel"><PanelTitle title={selected?`Shop ${selected}`:"Choose a shop"} subtitle="Drag rows to reorder. Click price or position to edit."/>
        {selected&&<><Autocomplete type="ITEM" value={null} onSelect={addItem} placeholder="Search item to add"/>
          <div className="rich-list">{items.map(row=><div className="rich-row draggable" key={row.shopitemid} draggable onDragStart={e=>e.dataTransfer.setData("row",JSON.stringify(row))} onDragOver={e=>e.preventDefault()} onDrop={e=>drag(JSON.parse(e.dataTransfer.getData("row")),row)}>
            <button className="icon-link" onClick={()=>onOpen("ITEM",row.itemid)}><img src={assetUrl("ITEM",row.itemid)} alt=""/></button>
            <button className="row-identity" onClick={()=>onOpen("ITEM",row.itemid)}><strong>{row.item_name||row.itemid}</strong><code>{row.itemid}</code></button>
            <InlineNumber value={row.position} save={v=>patch(row,"position",v)} label="Position"/><InlineNumber value={row.price} save={v=>patch(row,"price",v)} label="Price"/>
            <button className="danger-icon" onClick={()=>remove(row)}><Trash2 size={15}/></button></div>)}</div></>}</article></div></>
}

function Gachapon({notify,onOpen}:{notify:Notify;onOpen:(type:string,id:number)=>void}){
  const [locations,setLocations]=useState<any[]>([]);const [selected,setSelected]=useState<string>();const [rows,setRows]=useState<any[]>([]);const [page,setPage]=useState(0);const [query,setQuery]=useState("");
  const loadLocations=()=>api<any[]>("/api/gachapon").then(setLocations);const load=async()=>{if(selected)setRows(await api<any[]>(`/api/gachapon/${selected}`))};
  useEffect(()=>{loadLocations()},[]);useEffect(()=>{load()},[selected]);
  async function add(item:Entity){if(!selected)return;const tier=Number(prompt("Tier: 0 common, 1 uncommon, 2 rare","0"));await api(`/api/gachapon/${selected}`,{method:"POST",body:JSON.stringify({tier,itemId:item.entity_id,npcId:null,enabled:true,reason:"Added in Console"})});notify("Gachapon override updated");load()}
  async function remove(row:any){if(!confirm("Remove reward?"))return;await api(`/api/gachapon/${selected}/${row.id}?reason=Deleted%20from%20Console`,{method:"DELETE"});load()}
  const filteredLocations=locations.filter(x=>!query||gachaponTown(x.location_code).toLowerCase().includes(query.toLowerCase())||String(x.npc_id||"").includes(query));
  return <><PageToolbar query={query} onQueryChange={v=>{setQuery(v);setPage(0)}} placeholder="Search gachapon town or NPC ID"/><div className="source-banner"><Ticket size={18}/><div><strong>Live database override</strong><span>The game server reads these rows first and falls back to the original Java reward arrays when a location has no Console entries.</span></div></div>
    <div className="split-list"><div className="list-column"><Pager page={page} pages={Math.ceil(filteredLocations.length/8)} setPage={setPage}/><div className="list-panel">{filteredLocations.slice(page*8,page*8+8).map(x=><button key={x.location_code} className={selected===x.location_code?"selected":""} onClick={()=>setSelected(x.location_code)}>
      {x.npc_id?<EntityImage className="list-avatar" type="NPC" id={x.npc_id}/>:<Ticket className="list-avatar"/>}<span><strong>{x.location_code==="GLOBAL"?"Global Gachapon":`Gachapon: ${gachaponTown(x.location_code)}`}</strong><small>{x.item_count} rewards | {x.region_name||"Global pool"}</small><small>{x.npc_id||"GLOBAL"}</small></span><ChevronRight size={16}/></button>)}</div><Pager page={page} pages={Math.ceil(filteredLocations.length/8)} setPage={setPage}/></div>
      <article className="panel"><PanelTitle title={selected?(selected==="GLOBAL"?"Global Gachapon":`Gachapon: ${gachaponTown(selected)}`):"Choose a gachapon"} subtitle="Common 90%, uncommon 8%, rare 2% before global-pool mixing"/>
        {selected&&<><Autocomplete type="ITEM" value={null} onSelect={add} placeholder="Search reward to add"/>
          <div className="rich-list">{rows.map(row=><div className="rich-row" key={row.id}><img src={assetUrl("ITEM",row.item_id)} alt=""/><button className="row-identity" onClick={()=>onOpen("ITEM",row.item_id)}><strong>{row.item_name||row.item_id}</strong><code>{row.item_id}</code></button>
            <span className={`tier tier-${row.tier}`}>{["Common","Uncommon","Rare"][row.tier]}</span><small className="source-line">{row.source_kind}</small><button className="danger-icon" onClick={()=>remove(row)}><Trash2 size={15}/></button></div>)}</div></>}</article></div></>
}

function Accounts({notify,onCharacter}:{notify:Notify;onCharacter:(id:number)=>void}){
  const [query,setQuery]=useState("");const [sort,setSort]=useState("lastlogin");const [direction,setDirection]=useState("desc");const [page,setPage]=useState(0);
  const [data,setData]=useState<Page<any>>({items:[],page:0,size:50,total:0,pages:0});const [selected,setSelected]=useState<any>();const [characters,setCharacters]=useState<any[]>([]);
  const load=()=>api<Page<any>>(`/api/accounts?query=${encodeURIComponent(query)}&sort=${sort}&direction=${direction}&page=${page}&size=50`).then(setData);
  useEffect(()=>{const t=setTimeout(load,180);return()=>clearTimeout(t)},[query,sort,direction,page]);
  async function choose(row:any){setSelected(row);setCharacters(await api<any[]>(`/api/accounts/${row.id}/characters`))}
  async function saveAccount(field:string,value:any){const row={...selected,[field]:value};const updated=await api<any>(`/api/accounts/${row.id}`,{method:"PATCH",body:JSON.stringify({banned:!!row.banned,banReason:row.banreason||"",mute:!!row.mute,nxCredit:Number(row.nxCredit||0),maplePoint:Number(row.maplePoint||0),nxPrepaid:Number(row.nxPrepaid||0),characterSlots:Number(row.characterslots||3),reason:`Inline ${field} update`})});setSelected(updated);notify("Account updated");load()}
  return <><PageToolbar query={query} onQueryChange={setQuery} placeholder="Account, email, character name or ID"><select value={sort} onChange={e=>setSort(e.target.value)}><option value="lastlogin">Last login</option><option value="id">Account ID</option><option value="name">Account name</option><option value="created">Created date</option><option value="characters">Character count</option></select><button className="secondary" onClick={()=>setDirection(direction==="asc"?"desc":"asc")}>{direction}</button></PageToolbar>
    <div className="split-list"><div className="list-column"><Pager page={page} pages={data.pages} setPage={setPage}/><div className="list-panel">{data.items.map(row=><button key={row.id} className={selected?.id===row.id?"selected":""} onClick={()=>choose(row)}>
      <div className="account-orb">{row.name.slice(0,2).toUpperCase()}</div><span><strong>{row.name}</strong><small>#{row.id} | {row.character_names||"No characters"}</small></span>{row.banned&&<span className="danger-tag">Banned</span>}</button>)}</div><Pager page={page} pages={data.pages} setPage={setPage}/></div>
      <article className="panel">{selected?<><PanelTitle title={selected.name} subtitle={`${selected.email||"No email"} | ${selected.loggedin?"Online":"Offline"}`}/>
        <div className="field-grid"><EditableField label="NX credit" value={selected.nxCredit||0} save={v=>saveAccount("nxCredit",v)}/><EditableField label="Maple points" value={selected.maplePoint||0} save={v=>saveAccount("maplePoint",v)}/>
          <EditableField label="Prepaid NX" value={selected.nxPrepaid||0} save={v=>saveAccount("nxPrepaid",v)}/><EditableField label="Character slots" value={selected.characterslots||3} save={v=>saveAccount("characterslots",v)}/>
          <ToggleField label="Banned" value={!!selected.banned} save={v=>saveAccount("banned",v)}/><ToggleField label="Muted" value={!!selected.mute} save={v=>saveAccount("mute",v)}/></div>
        <h3 className="section-heading">Characters</h3><div className="character-grid">{characters.map(c=><button className="character-card" key={c.id} onClick={()=>onCharacter(c.id)}>
          <div className="avatar-placeholder"><CircleUserRound/></div><strong>{c.name}</strong><span>Lv. {c.level} | {c.job_name||"Unknown job"} ({c.job})</span><small>{Number(c.meso).toLocaleString()} mesos | {c.map_name||"Unknown map"} ({c.map})</small></button>)}</div>
      </>:<Empty text="Choose an account to inspect and edit"/>}</article></div></>
}

function Inventory({notify,focusCharacter,onOpen,jump,onInspectorChange}:{notify:Notify;focusCharacter?:number;onOpen:(type:string,id:number)=>void;jump:(x:Jump)=>void;onInspectorChange:(size:"none"|"standard"|"wide")=>void}){
  const [character,setCharacter]=useState<any|null>(null);const [items,setItems]=useState<any[]>([]);const [editing,setEditing]=useState<any|null>(null);const [editingStorage,setEditingStorage]=useState<any|null>(null);const [storage,setStorage]=useState<any[]>([]);const [editingCharacter,setEditingCharacter]=useState(false);
  const [accounts,setAccounts]=useState<any[]>([]);const [browseAccount,setBrowseAccount]=useState<any>();const [accountCharacters,setAccountCharacters]=useState<any[]>([]);
  useEffect(()=>{
    onInspectorChange(editing?"wide":editingStorage||editingCharacter?"standard":"none");
    return()=>onInspectorChange("none");
  },[editing,editingStorage,editingCharacter,onInspectorChange]);
  useEffect(()=>{api<Page<any>>("/api/accounts?sort=name&direction=asc&page=0&size=200").then(r=>setAccounts(r.items))},[]);
  async function chooseAccount(account:any){setBrowseAccount(account);setAccountCharacters(await api<any[]>(`/api/accounts/${account.id}/characters`))}
  useEffect(()=>{if(focusCharacter)api<any[]>(`/api/characters/search?query=${focusCharacter}`).then(r=>r[0]&&setCharacter(r[0]))},[focusCharacter]);
  async function load(){if(!character)return;setItems(await api<any[]>(`/api/characters/${character.id}/inventory`));setStorage(await api<any[]>(`/api/accounts/${character.accountid}/storage?world=${character.world}`))}
  useEffect(()=>{if(character)load()},[character]);
  async function move(item:any,position:number){await api(`/api/characters/${character.id}/inventory/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify({...item,itemId:item.itemid,position,quantity:item.quantity||1,equipment:item.inventorytype===1?equipFrom(item):null,reason:"Moved in Console"})});load()}
  async function moveOrSwap(item:any,target:any,position:number){if(target)await api(`/api/characters/${character.id}/inventory/swap`,{method:"POST",body:JSON.stringify({firstItemId:item.inventoryitemid,secondItemId:target.inventoryitemid,reason:"Swapped in Console"})});else await move(item,position);load()}
  async function duplicate(item:any){await api(`/api/characters/${character.id}/inventory/${item.inventoryitemid}/duplicate`,{method:"POST",body:JSON.stringify({reason:"Duplicated in Console"})});notify("Item duplicated into next empty slot");load()}
  async function moveStorage(item:any,position:number){await api(`/api/accounts/${character.accountid}/storage/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify({world:character.world,position,quantity:item.quantity||1,equipment:item.inventorytype===1?equipFrom(item):null,reason:"Moved in Console"})});load()}
  async function moveOrSwapStorage(item:any,target:any,position:number){if(target)await api(`/api/accounts/${character.accountid}/storage/swap`,{method:"POST",body:JSON.stringify({world:character.world,firstItemId:item.inventoryitemid,secondItemId:target.inventoryitemid,reason:"Swapped in Console"})});else await moveStorage(item,position);load()}
  async function saveStorageMeso(value:number){await api(`/api/accounts/${character.accountid}/storage`,{method:"PATCH",body:JSON.stringify({world:character.world,slots:Number(storage[0]?.slots||48),meso:value,reason:"Updated storage mesos through Console"})});notify("Storage mesos updated");load()}
  async function expandStorage(){await api(`/api/accounts/${character.accountid}/storage`,{method:"PATCH",body:JSON.stringify({world:character.world,slots:48,meso:Number(storage[0]?.meso||0),reason:"Expanded account storage to 48 slots"})});notify("Storage expanded to 48 slots");load()}
  const limits=[0,character?.equipslots||96,character?.useslots||96,character?.setupslots||96,character?.etcslots||96,96];
  const groups=useMemo(()=>[1,2,3,4,5].map(type=>({type,items:items.filter(x=>x.inventorytype===type)})),[items]);
  return <><AutocompleteCharacter value={character} onSelect={setCharacter}/><div className="inventory-browser"><div><h3>Browse accounts</h3>{accounts.map(a=><button className={browseAccount?.id===a.id?"selected":""} key={a.id} onClick={()=>chooseAccount(a)}><UsersRound/><span><strong>{a.name}</strong><small>{a.character_count} characters</small></span></button>)}</div>
    <div><h3>{browseAccount?`${browseAccount.name}'s characters`:"Choose an account"}</h3>{accountCharacters.map(c=><button key={c.id} onClick={()=>setCharacter({...c,accountid:browseAccount.id,account_name:browseAccount.name})}><CircleUserRound/><span><strong>{browseAccount.name} → {c.name}</strong><small>Lv. {c.level} {c.job_name||"Unknown job"} ({c.job})</small></span></button>)}</div></div>
    {character&&<div className="character-strip"><CircleUserRound/><div><strong>{character.account_name} → {character.name}</strong><span>Lv. {character.level} | {character.job_name||"Unknown job"} ({character.job}) | {character.map_name||"Unknown map"} ({character.map})</span></div><button className="secondary" onClick={()=>setEditingCharacter(true)}>Edit stats</button><button className="secondary" onClick={load}><RefreshCw size={14}/>Refresh</button></div>}
    {character&&<>{groups.map(group=><article className="panel inventory-panel" key={group.type}><PanelTitle title={["","Equip","Use","Setup","Etc","Cash"][group.type]} subtitle="Click an item to edit. Drag onto an empty slot to move."/>
      <div className="slot-grid expanded">{Array.from({length:limits[group.type]},(_,index)=>index+1).map(slot=>{const item=group.items.find(x=>x.position===slot);return <button key={slot} className={`item-slot ${item?"occupied":""}`}
        draggable={!!item} onDragStart={e=>item&&e.dataTransfer.setData("item",JSON.stringify(item))} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();const dragged=JSON.parse(e.dataTransfer.getData("item"));if(dragged.inventoryitemid!==item?.inventoryitemid)moveOrSwap(dragged,item,slot)}}
        onClick={()=>item?setEditing(item):setEditing({newItem:true,position:slot,inventorytype:group.type})} title={item?itemTooltip(item):`Empty ${slot}`}>
        <span>{slot}</span>{item&&<><img src={assetUrl("ITEM",item.itemid)} alt=""/>{item.quantity>1&&<b>{item.quantity}</b>}</>}</button>})}</div></article>)}
      <article className="panel inventory-panel"><div className="storage-heading"><PanelTitle title="Account storage" subtitle={`World ${character.world}`}/>
        <div className="storage-tools"><div className="storage-capacity"><strong>{storage[0]?.slots||0}</strong><span>of 48 slots</span>{Number(storage[0]?.slots||0)<48&&<button className="secondary" onClick={expandStorage}>Expand to 48</button>}</div>
          <StorageMesoEditor value={Number(storage[0]?.meso||0)} save={saveStorageMeso}/></div></div>
        <div className="slot-grid expanded">{Array.from({length:storage[0]?.slots||48},(_,i)=>i).map(slot=>{const item=storage.find(x=>x.inventoryitemid&&x.position===slot);return <button className={`item-slot ${item?"occupied":""}`} key={slot}
          draggable={!!item} onDragStart={e=>item&&e.dataTransfer.setData("storageItem",JSON.stringify(item))} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();const dragged=JSON.parse(e.dataTransfer.getData("storageItem"));if(dragged.inventoryitemid!==item?.inventoryitemid)moveOrSwapStorage(dragged,item,slot)}}
          onClick={()=>setEditingStorage(item||{newItem:true,position:slot})} title={item?itemTooltip(item):`Empty storage slot ${slot}`}><span>{slot}</span>{item&&<img src={assetUrl("ITEM",item.itemid)} alt=""/>}</button>})}</div></article></>}
    {editing&&character&&<ItemEditor key={`${editing.newItem?"new":editing.inventoryitemid}-${editing.position}`} item={editing} characterId={character.id} close={()=>setEditing(null)} saved={()=>{setEditing(null);load()}} notify={notify} duplicate={duplicate} onOpen={onOpen} jump={jump}/>}
    {editingStorage&&character&&<StorageItemEditor item={editingStorage} accountId={character.accountid} world={character.world} close={()=>setEditingStorage(null)} saved={()=>{setEditingStorage(null);load()}} notify={notify}/>}
    {editingCharacter&&character&&<CharacterEditor characterId={character.id} close={()=>setEditingCharacter(false)} saved={updated=>{setCharacter({...character,...updated});setEditingCharacter(false);notify("Character updated")}}/>}</>
}

function CharacterEditor({characterId,close,saved}:{characterId:number;close:()=>void;saved:(x:any)=>void}){
  const [data,setData]=useState<any>();useEffect(()=>{api(`/api/characters/${characterId}`).then(setData)},[characterId]);
  async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();const f=new FormData(e.currentTarget);const value=(name:string)=>Number(f.get(name)||0);
    const updated=await api<any>(`/api/characters/${characterId}`,{method:"PATCH",body:JSON.stringify({level:value("level"),job:value("job"),str:value("str"),dex:value("dex"),intStat:value("int"),luk:value("luk"),hp:value("hp"),mp:value("mp"),maxHp:value("maxhp"),maxMp:value("maxmp"),ap:value("ap"),meso:value("meso"),fame:value("fame"),map:value("map"),reason:String(f.get("reason"))})});saved(updated)}
  if(!data)return <div className="drawer inspector"><Loading/></div>;
  const fields=[["level","Level"],["job","Job ID"],["str","STR"],["dex","DEX"],["int","INT"],["luk","LUK"],["hp","HP"],["mp","MP"],["maxhp","Max HP"],["maxmp","Max MP"],["ap","Available AP"],["meso","Mesos"],["fame","Fame"],["map","Map ID"]];
  return <form className="drawer inspector editor-dock" onSubmit={submit}><button type="button" className="modal-close" onClick={close}><X/></button>
    <PanelTitle title={`Edit ${data.account_name} → ${data.name}`} subtitle={`${data.job_name||"Unknown job"} (${data.job}) | ${data.map_name||"Unknown map"} (${data.map}) | account must be offline`}/>
    <div className="field-grid">{fields.map(([key,label])=><label key={key}>{label}<input name={key} type="number" defaultValue={data[key]||0}/></label>)}</div>
    <label>Audit reason<textarea name="reason" required defaultValue="Edited through character manager"/></label><div className="modal-actions"><button className="primary">Save character</button></div>
  </form>
}

function ItemEditor({item,characterId,close,saved,notify,duplicate,onOpen,jump}:{item:any;characterId:number;close:()=>void;saved:()=>void;notify:Notify;duplicate:(x:any)=>void;onOpen:(t:string,id:number)=>void;jump:(x:Jump)=>void}){
  const [selected,setSelected]=useState<Entity|null>(null);
  const [original,setOriginal]=useState<any>();useEffect(()=>{const id=selected?.entity_id||item.itemid;if(id)api(`/api/catalog/ITEM/${id}`).then(setOriginal)},[selected,item.itemid]);
  const equipmentFields=[
    ["upgradeSlots","Upgrade slots remaining","upgradeslots"],["level","Upgrades applied","upgrades"],
    ["str","STR","str"],["dex","DEX","dex"],["intStat","INT","int"],["luk","LUK","luk"],
    ["hp","HP","hp"],["mp","MP","mp"],["watk","Weapon attack","watk"],["matk","Magic attack","matk"],
    ["wdef","Weapon defense","wdef"],["mdef","Magic defense","mdef"],["acc","Accuracy","acc"],
    ["avoid","Avoidability","avoid"],["hands","Hands","hands"],["speed","Speed","speed"],
    ["jump","Jump","jump"],["locked","Locked flag","locked"],["vicious","Vicious Hammer uses","vicious"],
    ["itemLevel","Item level","itemlevel"],["itemExp","Item EXP","itemexp"]
  ] as const;
  const fieldByName=Object.fromEntries(equipmentFields.map(field=>[field[0],field]));
  const renderEquipmentGroup=(names:string[],className="")=><div className={`equipment-stat-row ${className}`}>{names.map(name=>{
    const [fieldName,label,key]=fieldByName[name];return <label key={fieldName}>{label}<input name={fieldName} type="number"
      min={["upgradeSlots","level","locked","vicious","itemLevel","itemExp"].includes(fieldName)?"0":undefined}
      defaultValue={item[key]??(fieldName==="itemLevel"?1:0)}/></label>})}</div>;
  async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();const f=new FormData(e.currentTarget);
    const equipment=item.inventorytype===1?Object.fromEntries(equipmentFields.map(([name])=>[name,Number(f.get(name)??0)])):null;
    const payload={itemId:selected?.entity_id||item.itemid,position:Number(f.get("position")),quantity:item.inventorytype===1?1:Number(f.get("quantity")),
      owner:String(f.get("owner")??""),flag:Number(f.get("flag")??0),expiration:Number(f.get("expiration")??-1),
      giftFrom:String(f.get("giftFrom")??""),equipment,reason:String(f.get("reason"))};
    if(item.newItem){if(!selected)return;await api(`/api/characters/${characterId}/inventory`,{method:"POST",body:JSON.stringify(payload)})}
    else await api(`/api/characters/${characterId}/inventory/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify(payload)});
    notify(item.newItem?"Item added":"Item updated");saved()}
  async function remove(){if(!confirm("Delete this item permanently?"))return;await api(`/api/characters/${characterId}/inventory/${item.inventoryitemid}?reason=Deleted%20from%20Console`,{method:"DELETE"});saved()}
  const originalProps=metadata(original?.properties_json);
  const editSection=<section className="inventory-edit-section"><h3>{item.newItem?"New item values":"Current saved item values"}</h3>
    <Autocomplete type="ITEM" subtype={inventoryItemTypes[item.inventorytype-1]} value={selected} onSelect={setSelected} placeholder={item.newItem?"Search compatible item":"Search to replace with another compatible item"}/>
    {!item.newItem&&<div className="record-identifiers"><span>Inventory record <code>{item.inventoryitemid}</code></span><span>Inventory type <code>{item.inventorytype}</code></span>
      <span>Pet link <code>{item.petid??-1}</code></span>{item.inventorytype===1&&<span>Ring link <code>{item.ringid??-1}</code></span>}</div>}
    <p className="edit-hint">Click any value below, type the replacement value, then save. Relationship IDs are shown above but remain read-only to protect linked pet and ring records.</p>
    <div className="field-grid editable-grid">
      <label>Position<input name="position" type="number" min="1" defaultValue={item.position??1}/></label>
      <label>Quantity<input name="quantity" type="number" min="1" disabled={item.inventorytype===1} defaultValue={item.inventorytype===1?1:item.quantity??1}/></label>
      <label>Owner<input name="owner" defaultValue={item.owner??""}/></label>
      <label>Item flag<input name="flag" type="number" min="0" defaultValue={item.flag??0}/></label>
      <label>Expiration timestamp<input name="expiration" type="number" defaultValue={item.expiration??-1}/></label>
      <label>Gift from<input name="giftFrom" maxLength={26} defaultValue={item.giftFrom??""}/></label>
    </div>
    {item.inventorytype===1&&<><h3>Current equipment stats</h3><p className="edit-hint">These are the exact values stored on this equipment, not its WZ average.</p>
      <div className="equipment-stat-groups editable-grid">
        {renderEquipmentGroup(["str","dex","intStat","luk"],"four")}
        {renderEquipmentGroup(["watk","matk"],"two")}
        {renderEquipmentGroup(["hp","mp","wdef","mdef"],"four")}
        {renderEquipmentGroup(["speed","jump","hands"],"three")}
        {renderEquipmentGroup(["acc","avoid"],"two")}
        {renderEquipmentGroup(["level","upgradeSlots"],"two")}
        {renderEquipmentGroup(["locked","vicious","itemLevel","itemExp"],"four")}
      </div></>}
    <label>Audit reason<textarea name="reason" required defaultValue={item.newItem?"Granted through inventory editor":"Edited through inventory editor"}/></label>
    <div className="modal-actions">{!item.newItem&&<><button type="button" className="danger-button" onClick={remove}><Trash2 size={15}/>Delete</button><button type="button" className="secondary" onClick={()=>duplicate(item)}><PackagePlus size={15}/>Duplicate</button></>}<button className="primary">Save</button></div>
  </section>;
  return <form className="drawer inspector editor-dock inventory-editor-dock" onSubmit={submit}><button type="button" className="modal-close" onClick={close}><X/></button>
    <div className="inventory-values-column">{editSection}</div>
    <div className="inventory-info-column">
      {original?<div className="drawer-hero inventory-item-hero"><img src={assetUrl("ITEM",original.entity_id)} alt=""/><div><div className="tag-row"><span className="tag">ITEM</span><span className="tag soft">{original.category||original.subtype}</span></div><h2>{original.name}</h2><code>{original.entity_id}</code><p>{original.description||"No String.wz description"}</p></div></div>
        :<PanelTitle title={item.newItem?`Add item to slot ${item.position}`:item.item_name||`Item ${item.itemid}`} subtitle="Loading catalog information"/>}
      {originalProps.statRanges&&<section><h3>Average stats and server roll range</h3><StatTable ranges={originalProps.statRanges}/></section>}
      {original&&<><LinkedRows type="MOB" title="Dropped by" rows={original.droppedBy} labelKey="name" idKey="id" collapsible click={r=>{close();onOpen("MOB",r.id)}}/>
        <LinkedRows type="NPC" title="Sold by" rows={original.soldBy} labelKey="name" idKey="id" collapsible click={r=>{close();onOpen("NPC",r.id)}}/>
        <LinkedRows type="GACHA" title="Available from gachapon" rows={original.gachapon} labelKey="location_code" idKey="npc_id" collapsible click={()=>{close();jump({view:"gacha"})}}/>
        <LinkedRows type="CHARACTER" title="Owned by characters" rows={original.ownedBy} labelKey="name" idKey="id" collapsible defaultOpen={false} click={r=>{close();jump({view:"inventory",id:r.id})}}/></>}
      {original&&<section className="technical"><h3>Technical provenance</h3><PropertyGrid data={originalProps}/><code className="source-code">WZ: {original.source_path}</code>
        <code className="source-code">WZ image node: {wzImageNode("ITEM",original.entity_id,original.source_path,metadata(original.properties_json))}</code>
        {assetUrls("ITEM",original.entity_id).map((source,index)=><code className="source-code" key={source}>Image {index===0?"source":"fallback"}: {source}</code>)}</section>}
    </div>
  </form>
}

function StorageItemEditor({item,accountId,world,close,saved,notify}:{item:any;accountId:number;world:number;close:()=>void;saved:()=>void;notify:Notify}){
  const [selected,setSelected]=useState<Entity|null>(null);
  async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();const f=new FormData(e.currentTarget);
    if(item.newItem){if(!selected)return;await api(`/api/accounts/${accountId}/storage`,{method:"POST",body:JSON.stringify({world,itemId:selected.entity_id,position:item.position,quantity:Number(f.get("quantity")),equipment:null,reason:String(f.get("reason"))})})}
    else await api(`/api/accounts/${accountId}/storage/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify({world,position:Number(f.get("position")),quantity:Number(f.get("quantity")),equipment:item.inventorytype===1?equipFrom(item):null,reason:String(f.get("reason"))})});
    notify(item.newItem?"Storage item added":"Storage item updated");saved()}
  async function remove(){if(!confirm("Delete this storage item?"))return;await api(`/api/accounts/${accountId}/storage/${item.inventoryitemid}?world=${world}&reason=Deleted%20from%20Console`,{method:"DELETE"});saved()}
  return <form className="drawer inspector editor-dock" onSubmit={submit}><button type="button" className="modal-close" onClick={close}><X/></button>
    <PanelTitle title={item.newItem?`Add storage item to slot ${item.position}`:item.item_name||`Item ${item.itemid}`} subtitle="Storage accepts all inventory types"/>
    {item.newItem?<Autocomplete type="ITEM" value={selected} onSelect={setSelected} placeholder="Search any item"/>:<SelectedEntity entity={{entity_type:"ITEM",entity_id:item.itemid,name:item.item_name}}/>}
    <div className="field-grid"><label>Position<input name="position" type="number" min="0" defaultValue={item.position}/></label><label>Quantity<input name="quantity" type="number" min="1" defaultValue={item.quantity||1}/></label></div>
    <label>Audit reason<textarea name="reason" required defaultValue={item.newItem?"Added through storage editor":"Edited through storage editor"}/></label>
    <div className="modal-actions">{!item.newItem&&<button type="button" className="danger-button" onClick={remove}><Trash2 size={15}/>Delete</button>}<button className="primary">Save</button></div>
  </form>
}

function EntityDrawer({entity,close,jump,history,historyIndex,moveHistory,named}:{entity:{type:string;id:number;context?:string};close:()=>void;jump:(x:Jump)=>void;history:HistoryEntry[];historyIndex:number;moveHistory:(index:number)=>void;named:(type:string,id:number,name:string)=>void}){
  const [data,setData]=useState<any>();useEffect(()=>{setData(undefined);api<any>(`/api/catalog/${entity.type}/${entity.id}`).then(value=>{setData(value);named(entity.type,entity.id,value.name)})},[entity]);
  if(!data)return <div className="drawer"><button className="modal-close" onClick={close}><X/></button><Loading/></div>;
  const props=metadata(data.properties_json);
  const imageSources=assetUrls(entity.type,entity.id,props);
  return <div className="drawer"><button className="modal-close" onClick={close}><X/></button><div className="drawer-history"><button disabled={historyIndex<=0} onClick={()=>moveHistory(historyIndex-1)}><ChevronLeft/><span>{history[historyIndex-1]?.name||"Back"}</span></button><select value={historyIndex} onChange={e=>moveHistory(Number(e.target.value))}>{history.map((entry,index)=><option value={index} key={`${entry.type}-${entry.id}-${index}`}>{entry.name||`${entry.type} ${entry.id}`}</option>)}</select><button disabled={historyIndex>=history.length-1} onClick={()=>moveHistory(historyIndex+1)}><span>{history[historyIndex+1]?.name||"Forward"}</span><ChevronRight/></button></div>
    <div className="drawer-hero"><EntityImage type={entity.type} id={entity.id} properties={props}/><div>
    <div className="tag-row"><span className="tag">{entity.type}</span>{data.subtype&&<span className="tag soft">{data.subtype}</span>}{data.category&&<span className="tag soft">{data.category}</span>}{data.used_in_game&&<span className="tag used">Used in server data</span>}</div>
    <h2>{data.name}</h2><code>{entity.id}</code><p>{data.description||"No String.wz description"}</p></div></div>
    {props.statRanges&&<section><h3>Average stats and server roll range</h3><StatTable ranges={props.statRanges}/></section>}
    {entity.type==="MOB"&&<>{entity.context!=="drops"&&<LinkedRows type="ITEM" title="Drops" rows={(data.drops||[]).filter((r:any)=>r.itemid>0)} labelKey="item_name" idKey="itemid" click={r=>jump({view:"items",type:"ITEM",id:r.itemid})}/>}
      <LinkedRows type="MAP" title="Spawn maps" rows={data.spawns} labelKey="map_name" idKey="map_id" click={r=>jump({view:"maps",type:"MAP",id:r.map_id})}/></>}
    {entity.type==="ITEM"&&<><LinkedRows type="MOB" title="Dropped by" rows={data.droppedBy} labelKey="name" idKey="id" collapsible click={r=>jump({view:"mobs",type:"MOB",id:r.id})}/>
      <LinkedRows type="NPC" title="Sold by" rows={data.soldBy} labelKey="name" idKey="id" collapsible click={r=>{close();jump({view:"shops",id:r.shopid})}}/>
      <LinkedRows type="GACHA" title="Available from gachapon" rows={data.gachapon} labelKey="location_code" idKey="npc_id" collapsible click={()=>{close();jump({view:"gacha"})}}/>
      <LinkedRows type="CHARACTER" title="Owned by characters" rows={data.ownedBy} labelKey="name" idKey="id" collapsible defaultOpen={false} click={r=>{close();jump({view:"inventory",id:r.id})}}/></>}
    {entity.type==="NPC"&&<><LinkedRows type="MAP" title="NPC locations" rows={data.locations} labelKey="map_name" idKey="map_id" click={r=>jump({view:"maps",type:"MAP",id:r.map_id})}/>
      <LinkedRows type="SHOP" title="NPC shops" rows={data.shops} labelKey="shopid" idKey="shopid" click={r=>{close();jump({view:"shops",id:r.shopid})}}/></>}
    {entity.type==="MAP"&&<><LinkedRows type="MAP" title="Portal destinations" rows={data.portals} labelKey="target_map_name" idKey="target_map_id" click={r=>jump({view:"maps",type:"MAP",id:r.target_map_id})}/>
      <LinkedRows type="MOB" title="Monsters on this map" rows={data.mobs} labelKey="name" idKey="entity_id" click={r=>jump({view:"mobs",type:"MOB",id:r.entity_id})}/>
      <LinkedRows type="NPC" title="NPCs on this map" rows={data.npcs} labelKey="name" idKey="entity_id" click={r=>jump({view:"npcs",type:"NPC",id:r.entity_id})}/></>}
    {entity.type==="SKILL"&&<><section><h3>{data.job_name||"Unknown job"} <small>Job {data.job_id}</small></h3></section><section><h3>Skill levels</h3><div className="skill-levels">{(data.levels||[]).map((x:any)=><details key={x.skill_level}><summary>Level {x.skill_level}</summary><PropertyGrid data={metadata(x.properties_json)}/></details>)}</div></section></>}
    <section className="technical"><h3>Technical provenance</h3><PropertyGrid data={props}/><code className="source-code">WZ: {data.source_path}</code>
      <code className="source-code">WZ image node: {wzImageNode(entity.type,entity.id,data.source_path,props)}</code>
      {imageSources.map((source,index)=><code className="source-code" key={source}>Image {index===0?"source":"fallback"}: {source}</code>)}
      <code className="source-code">SQL: cosmic_database_console.catalog_entities WHERE entity_type='{entity.type}' AND entity_id={entity.id}</code></section>
  </div>
}

function Autocomplete({type,subtype="",value,onSelect,placeholder}:{type:string;subtype?:string;value:Entity|null;onSelect:(x:Entity)=>void;placeholder:string}){
  const [query,setQuery]=useState("");const [rows,setRows]=useState<Entity[]>([]);
  useEffect(()=>{if(query.trim().length<1){setRows([]);return}const t=setTimeout(()=>api<Entity[]>(`/api/catalog/suggest?q=${encodeURIComponent(query)}&type=${type}&subtype=${subtype}`).then(setRows),160);return()=>clearTimeout(t)},[query,type,subtype]);
  return <div className="autocomplete">{value&&<div className="autocomplete-current"><EntityImage type={type} id={value.entity_id} properties={metadata(value.properties_json)}/><span><strong>{value.name}</strong><small>{value.entity_id}</small></span></div>}
    <SearchInput value={query} setValue={setQuery} placeholder={value?"Search to replace selection":placeholder}/>
    {rows.length>0&&<div className="suggestions">{rows.map(row=><button type="button" key={row.entity_id} onClick={()=>{onSelect(row);setRows([]);setQuery("")}}><EntityImage type={type} id={row.entity_id} properties={metadata(row.properties_json)}/><span><strong>{row.name}</strong><small>{row.entity_id} | {row.subtype}</small><em>{row.description}</em></span></button>)}</div>}</div>
}
function AutocompleteCharacter({value,onSelect}:{value:any;onSelect:(x:any)=>void}){
  const [query,setQuery]=useState("");const [rows,setRows]=useState<any[]>([]);
  useEffect(()=>{if(!query){setRows([]);return}const t=setTimeout(()=>api<any[]>(`/api/characters/search?query=${encodeURIComponent(query)}`).then(setRows),160);return()=>clearTimeout(t)},[query]);
  return <div className="autocomplete character-search"><SearchInput value={value?`${value.name} (#${value.id})`:query} setValue={v=>{setQuery(v);if(value)onSelect(null)}} placeholder="Search character IGN, account or ID"/>
    {rows.length>0&&!value&&<div className="suggestions">{rows.map(row=><button key={row.id} onClick={()=>{onSelect(row);setRows([])}}><CircleUserRound/><span><strong>{row.account_name} → {row.name}</strong><small>Lv. {row.level} | {row.job_name||"Unknown job"} ({row.job}) | #{row.id}</small></span></button>)}</div>}</div>
}

function InlineNumber({value,save,label}:{value:number;save:(v:number)=>void;label:string}){const [editing,setEditing]=useState(false);const [draft,setDraft]=useState(String(value));
  return <label className="inline-number"><span>{label}</span>{editing?<input autoFocus value={draft} onChange={e=>setDraft(e.target.value)} onBlur={()=>{setEditing(false);if(Number(draft)!==value)save(Number(draft))}} onKeyDown={e=>e.key==="Enter"&&e.currentTarget.blur()}/>:<button onClick={()=>{setDraft(String(value));setEditing(true)}}>{Number(value).toLocaleString()}</button>}</label>}
function EditableField({label,value,save}:{label:string;value:number;save:(v:number)=>void}){return <div className="field-tile"><span>{label}</span><InlineNumber label="" value={value} save={save}/></div>}
function StorageMesoEditor({value,save}:{value:number;save:(v:number)=>void}){
  const [editing,setEditing]=useState(false);const [draft,setDraft]=useState(String(value));useEffect(()=>setDraft(String(value)),[value]);
  function commit(){const next=Math.max(0,Number(draft)||0);setEditing(false);if(next!==value)save(next)}
  return <div className="storage-meso"><Coins/><span><small>Storage mesos</small>{editing
    ?<input autoFocus type="number" min="0" value={draft} onChange={e=>setDraft(e.target.value)} onBlur={commit} onKeyDown={e=>e.key==="Enter"&&e.currentTarget.blur()}/>
    :<button onClick={()=>setEditing(true)}>{value.toLocaleString()}</button>}</span></div>
}
function ToggleField({label,value,save}:{label:string;value:boolean;save:(v:boolean)=>void}){return <label className="field-tile toggle"><span>{label}</span><input type="checkbox" checked={value} onChange={e=>save(e.target.checked)}/></label>}
function chanceDisplay(value:number){
  const percent=Math.min(100,value/10000);
  const one=value>0?Math.max(1,Math.round(1_000_000/value)):Infinity;
  return {percent:`${percent.toFixed(percent<1?3:2)}%`,one:`1 in ${Number.isFinite(one)?one.toLocaleString():"-"}`};
}
function Chance({value}:{value:number}){const display=chanceDisplay(value);return <div className="chance"><strong>{display.percent}</strong><span>{display.one}</span></div>}
function Pager({page,pages,setPage}:{page:number;pages:number;setPage:(n:number)=>void}){const [target,setTarget]=useState(String(page+1));useEffect(()=>setTarget(String(page+1)),[page]);if(pages<=1)return null;
  function go(){const next=Math.min(pages,Math.max(1,Number(target)||1));setPage(next-1)}
  return <div className="pager"><button disabled={page===0} onClick={()=>setPage(page-1)}><ChevronLeft/></button><span>{page+1} / {pages}</span><label>Go to<input type="number" min="1" max={pages} value={target} onChange={e=>setTarget(e.target.value)} onKeyDown={e=>e.key==="Enter"&&go()}/></label><button className="go-page" onClick={go}>Go</button><button disabled={page>=pages-1} onClick={()=>setPage(page+1)}><ChevronRight/></button></div>}
function SearchInput({value,setValue,placeholder}:{value:string;setValue:(x:string)=>void;placeholder:string}){return <div className="search-box"><Search size={17}/><input value={value} onChange={e=>setValue(e.target.value)} placeholder={placeholder}/></div>}
function EntityImage({type,id,properties={},className=""}:{type:string;id:number;properties?:Record<string,any>;className?:string}){
  const sources=assetUrls(type,id,properties);const [sourceIndex,setSourceIndex]=useState(0);useEffect(()=>setSourceIndex(0),[type,id,properties.imageAction]);
  if(sourceIndex>=sources.length){if(type==="SKILL")return <SkillBadge skillId={id} className={className}/>;
    const Icon=type==="MOB"?Skull:type==="MAP"?MapPinned:type==="NPC"?UsersRound:PackageSearch;return <span className={`image-fallback ${className}`}><Icon/></span>}
  return <img className={className} src={sources[sourceIndex]} alt="" onError={()=>setSourceIndex(index=>index+1)}/>;
}
function SkillBadge({skillId,className=""}:{skillId:number;className?:string}){return <span className={`skill-badge ${className}`}><BookOpen/><small>{String(skillId).slice(-2)}</small></span>}
function JobBadge({jobId}:{jobId:number}){const family=jobId===0?"BG":jobId<200?"WA":jobId<300?"MA":jobId<400?"BO":jobId<500?"TH":jobId<600?"PI":jobId>=1000&&jobId<2000?"CY":jobId>=2000?"HE":"GM";return <span className={`job-badge family-${family.toLowerCase()}`}>{family}</span>}
function SelectedEntity({entity}:{entity:Entity}){return <div className="selected-entity"><img src={assetUrl(entity.entity_type,entity.entity_id)} alt=""/><span><strong>{entity.name}</strong><code>{entity.entity_id}</code><small>{entity.description}</small></span></div>}
function StatStrip({ranges}:{ranges:Record<string,any>}){return <div className="stat-strip">{Object.entries(ranges).slice(0,4).map(([key,value]:any)=><span key={key}>{statName(key)} {value.average} ({value.min}-{value.max})</span>)}</div>}
function StatTable({ranges}:{ranges:Record<string,any>}){return <div className="stat-table">{Object.entries(ranges).map(([key,value]:any)=><div key={key}><strong>{statName(key)}</strong><span>{value.average}</span><small>{value.min} - {value.max}</small></div>)}</div>}
function LinkedRows({type,title,rows=[],labelKey,idKey,click,collapsible=false,defaultOpen=true}:{type:string;title:string;rows:any[];labelKey:string;idKey:string;click:(x:any)=>void;collapsible?:boolean;defaultOpen?:boolean}){
  const content=rows.length?<div className="linked-rows">{rows.map((row,i)=>{
    const chance=row.chance==null?null:chanceDisplay(Number(row.chance));
    return <button key={`${row[idKey]}-${i}`} onClick={()=>click(row)}><span className="linked-icon">{type==="CHARACTER"?<CircleUserRound/>:type==="SHOP"?<Store/>:type==="GACHA"?(row[idKey]?<EntityImage type="NPC" id={row[idKey]}/>:<Ticket/>):<EntityImage type={type} id={row[idKey]}/>}</span><span className="linked-copy"><strong>{type==="GACHA"?`Gachapon: ${gachaponTown(row[labelKey])}`:row[labelKey]||row[idKey]}</strong><code>{type==="GACHA"?`Tier ${row.tier}`:row[idKey]}</code>{row.region_name&&<small>{row.region_name} | {row.spawn_count} spawn points</small>}{chance&&<small className="linked-chance">{chance.percent} | {chance.one}</small>}</span><ChevronRight size={14}/></button>
  })}</div>:<p className="muted">No linked records.</p>;
  if(collapsible)return <details className="linked-section" open={defaultOpen}><summary>{title} <small>({rows.length})</small><ChevronRight size={16}/></summary>{content}</details>;
  return <section><h3>{title} <small>({rows.length})</small></h3>{content}</section>
}
function PropertyGrid({data}:{data:Record<string,any>}){return <div className="property-grid">{Object.entries(data||{}).filter(([,v])=>typeof v!=="object").map(([k,v])=><div key={k}><span>{k}</span><code>{String(v)}</code></div>)}</div>}
function PanelTitle({title,subtitle}:{title:string;subtitle:string}){return <div className="panel-title"><div><h2>{title}</h2><p>{subtitle}</p></div></div>}
function Status({label,value}:{label:string;value:string}){return <div className="status-row"><span>{label}</span><strong>{value}</strong></div>}
function Empty({text}:{text:string}){return <div className="empty"><PackageSearch size={32}/><p>{text}</p></div>}
function Loading(){return <div className="empty"><RefreshCw className="spin"/><p>Loading current data...</p></div>}
function Audit(){const [rows,setRows]=useState<any[]>([]);useEffect(()=>{api<any[]>("/api/audit").then(setRows)},[]);return <article className="panel"><PanelTitle title="Audit history" subtitle="Who changed what, when, why, and the exact saved values"/><div className="rich-list">{rows.map(row=><details className="audit-row" key={row.id}><summary><strong>{row.action}</strong><span>{row.username||"System"}</span><code>{row.entity_type}:{row.entity_key}</code><small>{row.created_at} | {row.outcome}</small></summary><p>{row.reason}</p><small>Remote address: {row.remote_address||"Unknown"}</small><div className="audit-change-grid"><AuditValue title="Before" value={row.before_json}/><AuditValue title="After" value={row.after_json}/></div></details>)}</div></article>}
function AuditValue({title,value}:{title:string;value:any}){const parsed=metadata(value);return <section><h3>{title}</h3>{value?<PropertyGrid data={flattenRecord(parsed)}/>:<p className="muted">No value</p>}</section>}

function metadata(value:any):Record<string,any>{if(!value)return{};if(typeof value==="object")return value;try{return JSON.parse(value)}catch{return{raw:value}}}
function flattenRecord(value:any,prefix=""):Record<string,any>{
  if(value==null||typeof value!=="object")return prefix?{[prefix]:value}:{value};
  return Object.entries(value).reduce((result,[key,nested])=>{
    const path=prefix?`${prefix}.${key}`:key;
    if(nested!=null&&typeof nested==="object"&&!Array.isArray(nested))Object.assign(result,flattenRecord(nested,path));
    else result[path]=Array.isArray(nested)?JSON.stringify(nested):nested;
    return result;
  },{} as Record<string,any>);
}
function gachaponTown(code:string){
  const names:Record<string,string>={
    GLOBAL:"Global",HENESYS:"Henesys",ELLINIA:"Ellinia",PERION:"Perion",KERNING_CITY:"Kerning City",
    SLEEPYWOOD:"Sleepywood",MUSHROOM_SHRINE:"Mushroom Shrine",SHOWA:"Showa",NLC:"New Leaf City",
    NAUTILUS:"Nautilus Harbor",ORBIS:"Orbis",LUDIBRIUM:"Ludibrium",EL_NATH:"El Nath",
    AQUARIUM:"Aquarium",LEAFRE:"Leafre",MU_LUNG:"Mu Lung",HERB_TOWN:"Herb Town",OMEGA_SECTOR:"Omega Sector"
  };
  return names[code]||code.toLowerCase().replaceAll("_"," ").replace(/\b\w/g,c=>c.toUpperCase());
}
function summaryFromProps(props:Record<string,any>){if(props.level)return `Level ${props.level} | ${Number(props.maxHP||0).toLocaleString()} HP`;if(props.price)return `Base price ${Number(props.price).toLocaleString()}`;return ""}
function wzImageNode(type:string,id:number,sourcePath:string|undefined,props:Record<string,any>){
  const source=sourcePath||"Not available";
  if(type==="MOB")return `${source}#${props.imageAction||"stand"}/0`;
  if(type==="ITEM")return `${source}#info/icon`;
  if(type==="NPC")return `${source}#stand/0`;
  if(type==="SKILL")return `${source}#skill/${id}/icon`;
  if(type==="MAP")return `${source}#miniMap`;
  return source;
}
function statName(key:string){return key.replace("inc","").replace("PAD","WATK").replace("MAD","MATK").replace("PDD","WDEF").replace("MDD","MDEF").replace("MHP","HP").replace("MMP","MP")}
function hideImage(e:any){e.currentTarget.style.visibility="hidden"}
function itemTooltip(item:any){const lines=[item.item_name||String(item.itemid),`Quantity: ${item.quantity}`];if(item.inventorytype===1){const stats=[["Upgrade slots",item.upgradeslots],["Upgrades",item.upgrades],["STR",item.str],["DEX",item.dex],["INT",item.int],["LUK",item.luk],["HP",item.hp],["MP",item.mp],["WATK",item.watk],["MATK",item.matk],["WDEF",item.wdef],["MDEF",item.mdef],["Accuracy",item.acc],["Avoidability",item.avoid],["Hands",item.hands],["Speed",item.speed],["Jump",item.jump],["Vicious",item.vicious],["Item level",item.itemlevel],["Item EXP",item.itemexp]];for(const [name,value] of stats)if(Number(value)!==0)lines.push(`${name}: ${value}`)}return lines.join("\n")}
function equipFrom(item:any){return {upgradeSlots:item.upgradeslots??0,level:item.upgrades??0,str:item.str??0,dex:item.dex??0,intStat:item.int??0,luk:item.luk??0,hp:item.hp??0,mp:item.mp??0,watk:item.watk??0,matk:item.matk??0,wdef:item.wdef??0,mdef:item.mdef??0,acc:item.acc??0,avoid:item.avoid??0,hands:item.hands??0,speed:item.speed??0,jump:item.jump??0,locked:item.locked??0,vicious:item.vicious??0,itemLevel:item.itemlevel??1,itemExp:item.itemexp??0}}
