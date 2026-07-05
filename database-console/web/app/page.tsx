"use client";

import {
  Activity, ArrowUpDown, BookOpen, Boxes, ChevronLeft, ChevronRight, CircleUserRound, Coins,
  Database, Eye, EyeOff, ExternalLink, GripVertical, MapPinned, PackagePlus, PackageSearch, RefreshCw,
  Search, Server, ShieldCheck, ShoppingBasket, Skull, Store, Ticket, Trash2, UserPlus, UsersRound, X
} from "lucide-react";
import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { ConsoleConnectionError } from "../components/auth/ConsoleAuth";
import { ConsoleNavItem, ConsoleShell } from "../components/layout/ConsoleShell";
import { PageToolbar } from "../components/ui/PageToolbar";
import { api, assetUrl, assetUrls, avatarUrl } from "../lib/api";

type View =
  "dashboard"
  | "accounts" | "create-account"
  | "character-stats" | "inventory" | "character-equipment"
  | "mobs"
  | "items"
  | "maps" | "npcs" | "shops" | "gacha"
  | "audit";
type Entity = {
  entity_type:string; entity_id:number; name:string; description?:string; category?:string;
  subtype?:string; level_value?:number; job_id?:number; job_name?:string; used_in_game?:boolean;
  source_path?:string; properties_json?:Record<string,any>|string;location_name?:string;
};
type Page<T>={items:T[];page:number;size:number;total:number;pages:number};
type Jump={view:View;id?:number;type?:string};
type JumpTarget=Jump&{location?:string};
type HistoryEntry={type:string;id:number;name?:string};
type Notify=(message:string)=>void;
type Toast={id:number;message:string};

const nav:readonly ConsoleNavItem<View>[] = [
  {key:"dashboard",label:"Dashboard",icon:Activity},
  {label:"Account",icon:UsersRound,children:[
    {key:"accounts",label:"Account Search",icon:UsersRound},
    {key:"create-account",label:"Create",icon:UserPlus},
  ]},
  {label:"Character",icon:CircleUserRound,children:[
    {key:"character-stats",label:"AP / SP & Stats",icon:Activity},
    {key:"inventory",label:"Inventory / Storage",icon:Boxes},
    {key:"character-equipment",label:"Equipment / Appearance",icon:ShieldCheck},
  ]},
  {label:"Mobs",icon:Skull,children:[
    {key:"mobs",label:"Mob Catalog / Drop Table",icon:Skull},
  ]},
  {label:"Items",icon:PackageSearch,children:[
    {key:"items",label:"Item Catalog",icon:PackageSearch},
  ]},
  {label:"World Data",icon:MapPinned,children:[
    {key:"maps",label:"Maps",icon:MapPinned},
    {key:"npcs",label:"NPCs",icon:UsersRound},
    {key:"shops",label:"Shops",icon:ShoppingBasket},
    {key:"gacha",label:"Gachapon",icon:Ticket},
  ]},
  {label:"Audit & Tools",icon:ShieldCheck,defaultCollapsed:true,children:[
    {key:"audit",label:"Global Audit",icon:ShieldCheck},
  ]},
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
const skinChoices=[{id:0,name:"Light",color:"#f5d2b6"},{id:1,name:"Tanned",color:"#c98d61"},{id:2,name:"Dark",color:"#83523a"},{id:3,name:"Pale",color:"#f4e7d3"},{id:4,name:"Blue",color:"#92bddf"},{id:5,name:"Green",color:"#9ccc9a"},{id:6,name:"Pink",color:"#f0b6c7"}];
const starterSkinChoices=skinChoices.filter(choice=>choice.id<=3);
const genderChoices=[{id:0,name:"Male",icon:"♂"},{id:1,name:"Female",icon:"♀"}];
const equipmentSlots=[
  [-1,"Hat"],[-2,"Face"],[-3,"Eye"],[-4,"Earrings"],[-5,"Top"],[-6,"Bottom"],[-7,"Shoes"],[-8,"Gloves"],
  [-9,"Cape"],[-10,"Shield"],[-11,"Weapon"],[-12,"Ring 1"],[-13,"Ring 2"],[-15,"Ring 3"],[-16,"Ring 4"],
  [-17,"Pendant"],[-18,"Mount"],[-19,"Saddle"],[-20,"Medal"],[-21,"Belt"],[-22,"Shoulder"],[-23,"Pocket"],
  [-24,"Badge"],[-25,"Emblem"]
] as const;
const cashEquipmentSlots=equipmentSlots.map(([position,label])=>[position-100,`Cash ${label}`] as const);
const starterEquipChoices={
  top:{0:[1040002,1040006,1040010],1:[1041002,1041006,1041010,1041011]},
  bottom:{0:[1060002,1060006],1:[1061002,1061008]},
  shoes:{0:[1072001,1072005,1072037,1072038],1:[1072001,1072005,1072037,1072038]},
  weapon:{0:[1302000,1322005,1312004],1:[1302000,1322005,1312004]}
} as const;
type StarterEquipSlot=keyof typeof starterEquipChoices;
const starterAppearanceChoices={
  hair:{
    0:[30030,30037,30033,30032,30020,30027,30023,30022,30000,30007,30003,30002],
    1:[31000,31007,31003,31002,31040,31047,31043,31042,31050,31057,31053,31052]
  },
  face:{0:[20000,20001,20002],1:[21000,21001,21002]}
} as const;
type StarterAppearanceSlot=keyof typeof starterAppearanceChoices;
const defaultStarter={hair:30030,face:20000,skincolor:0,gender:0,top:1040002,bottom:1060002,shoes:1072001,weapon:1302000};
const defaultUnderwearByGender={
  0:{top:1040002,bottom:1060002},
  1:{top:1041002,bottom:1061002}
} as const;

const characterTabs:readonly [View,string][]=[
  ["character-stats","AP / SP & Stats"],["inventory","Inventory / Storage"],
  ["character-equipment","Equipment / Appearance"]
];
const worldTabs:readonly [View,string][]=[
  ["maps","Maps"],["npcs","NPCs"],["shops","Shops"],["gacha","Gachapon"]
];
const auditTabs:readonly [View,string][]=[
  ["audit","Global Audit"]
];

export default function App(){
  const [view,setView]=useState<View>("dashboard");
  const [apiReady,setApiReady]=useState<boolean|null>(null);
  const [startupError,setStartupError]=useState("");
  const [theme,setTheme]=useState<"light"|"dark">("light");
  const [themeReady,setThemeReady]=useState(false);
  const [notices,setNotices]=useState<Toast[]>([]);
  const [drawer,setDrawer]=useState<{type:string;id:number;context?:string}|null>(null);
  const [accountDrawer,setAccountDrawer]=useState<{account:any;characters:any[]}|null>(null);
  const [embeddedInspector,setEmbeddedInspector]=useState<"none"|"standard"|"wide">("none");
  const [viewHistory,setViewHistory]=useState<HistoryEntry[]>([]);const [historyIndex,setHistoryIndex]=useState(-1);
  const [focusId,setFocusId]=useState<number>();
  const [focusLocation,setFocusLocation]=useState<string>();
  const [selectedCharacterId,setSelectedCharacterId]=useState<number>();
  const [createCharacterAccount,setCreateCharacterAccount]=useState<any|null>(null);
  function notify(message:string){const id=Date.now()+Math.random();setNotices(previous=>[...previous,{id,message}]);setTimeout(()=>setNotices(previous=>previous.filter(toast=>toast.id!==id)),3000)}

  useEffect(()=>{api("/api/setup/status").then(()=>setApiReady(true)).catch(()=>{
    setStartupError("The Console API is offline or its database credentials are not configured.");
    setApiReady(false);
  })},[]);
  useEffect(()=>{
    const saved=window.localStorage.getItem("database-console-theme");
    setTheme(saved==="dark"?"dark":"light");
    setThemeReady(true);
  },[]);
  useEffect(()=>{
    if(!themeReady)return;
    document.body.dataset.theme=theme;
    window.localStorage.setItem("database-console-theme",theme);
  },[theme,themeReady]);
  function openDrawer(type:string,id:number,context?:string){
    setAccountDrawer(null);
    setDrawer({type,id,context});setViewHistory(previous=>{
      const next=[...previous.slice(0,historyIndex+1),{type,id}].slice(-10);setHistoryIndex(next.length-1);return next;
    });
  }
  function jump(target:JumpTarget){setView(target.view);setFocusId(target.id);setFocusLocation(target.location);if(["character-stats","inventory","character-equipment"].includes(target.view))setSelectedCharacterId(target.id);if(target.type&&target.id)openDrawer(target.type,target.id)}
  function inspectEntity(type:string,id:number){openDrawer(type,id)}
  function moveHistory(index:number){const entry=viewHistory[index];if(!entry)return;setHistoryIndex(index);setDrawer({type:entry.type,id:entry.id})}
  function nameHistory(type:string,id:number,name:string){setViewHistory(previous=>previous.map(entry=>entry.type===type&&entry.id===id?{...entry,name}:entry))}
  if(apiReady===null)return <div className="splash">Preparing Cosmic Database Console...</div>;
  if(startupError)return <ConsoleConnectionError mark="DC" productName="Database Console" message={startupError}/>;
  const headerSection=sectionHeader(view);
  const headerTabs=headerSection&&<div className="planned-tabs header-tabs">{headerSection.tabs.map(([key,label],index)=><button type="button" className={index===headerSection.activeIndex?"active":""} key={`header-${headerSection.title}-${key}-${label}`} onClick={()=>setView(key)}>{label}</button>)}</div>;
  const inspector=accountDrawer
    ? <AccountDrawer account={accountDrawer.account} characters={accountDrawer.characters} close={()=>setAccountDrawer(null)} notify={notify} onCharacter={(targetView,id)=>{setAccountDrawer(null);jump({view:targetView,id})}}/>
    : drawer&&<EntityDrawer entity={drawer} close={()=>setDrawer(null)} jump={jump} history={viewHistory} historyIndex={historyIndex} moveHistory={moveHistory} named={nameHistory}/>;
  return <ConsoleShell activeView={view} brandMark="DC" brandSubtitle="Database Console" eyebrow={headerSection?.title.toUpperCase()||"COSMIC OPERATIONS"}
    headerTitle={headerSection?.label} headerTabs={headerTabs} inspectorOpen={Boolean(drawer)||Boolean(accountDrawer)||embeddedInspector!=="none"}
    inspectorSize={embeddedInspector==="wide"?"wide":"standard"} navigation={nav}
    onNavigate={next=>{setView(next);setFocusId(undefined);setDrawer(null);setAccountDrawer(null);setEmbeddedInspector("none")}}
    sidebarStatus={<><Database size={16}/> MySQL connected</>} theme={theme} onToggleTheme={()=>setTheme(current=>current==="dark"?"light":"dark")} inspector={inspector}>
      {notices.length>0&&<div className="notice-stack">{notices.map(toast=><button type="button" className="notice" key={toast.id} onClick={()=>setNotices(previous=>previous.filter(row=>row.id!==toast.id))}>{toast.message}</button>)}</div>}
        {view==="dashboard"&&<Dashboard/>}
        {view==="accounts"&&<SectionFrame title="Account" tabs={[["accounts","Account Search"],["create-account","Create"]]} active={view} setView={setView}><Accounts notify={notify} onCreateCharacter={account=>{setCreateCharacterAccount(account);setView("create-account")}} onInspect={(account,characters)=>{setDrawer(null);setAccountDrawer({account,characters})}}/></SectionFrame>}
        {view==="create-account"&&<SectionFrame title="Account" tabs={[["accounts","Account Search"],["create-account","Create"]]} active={view} setView={setView}><AccountCharacterCreate notify={notify} initialAccount={createCharacterAccount} onInspect={(account,characters)=>{setDrawer(null);setAccountDrawer({account,characters})}} onCreated={id=>jump({view:"character-stats",id})}/></SectionFrame>}
        {view==="character-stats"&&<SectionFrame title="Account > Character" tabs={characterTabs} active={view} setView={setView}><CharacterStats notify={notify} focusCharacter={focusId||selectedCharacterId} onCharacterSelect={setSelectedCharacterId}/></SectionFrame>}
        {view==="inventory"&&<SectionFrame title="Account > Character" tabs={characterTabs} active={view} setView={setView}><Inventory notify={notify} focusCharacter={focusId||selectedCharacterId} onCharacterSelect={setSelectedCharacterId} onOpen={inspectEntity} jump={jump} onInspectorChange={setEmbeddedInspector}/></SectionFrame>}
        {view==="character-equipment"&&<SectionFrame title="Account > Character" tabs={characterTabs} active={view} setView={setView}><EquipmentAppearance notify={notify} focusCharacter={focusId||selectedCharacterId} onCharacterSelect={setSelectedCharacterId} onOpen={inspectEntity} jump={jump} onInspectorChange={setEmbeddedInspector}/></SectionFrame>}
        {view==="items"&&<SectionFrame title="Items" tabs={[["items","Catalog"]]} active={view} setView={setView}><Library fixedType="ITEM" onOpen={inspectEntity}/></SectionFrame>}
        {view==="mobs"&&<SectionFrame title="Mobs" tabs={[["mobs","Catalog / Drop Table"]]} active={view} setView={setView}><Drops notify={notify} focusMob={focusId} onOpen={(type,id)=>openDrawer(type,id)}/></SectionFrame>}
        {view==="maps"&&<SectionFrame title="World Data" tabs={worldTabs} active={view} setView={setView}><Maps focusMap={focusId} onOpen={inspectEntity}/></SectionFrame>}
        {view==="npcs"&&<SectionFrame title="World Data" tabs={worldTabs} active={view} setView={setView}><Library fixedType="NPC" onOpen={inspectEntity}/></SectionFrame>}
        {view==="shops"&&<SectionFrame title="World Data" tabs={worldTabs} active={view} setView={setView}><Shops notify={notify} focusShop={focusId} onOpen={inspectEntity}/></SectionFrame>}
        {view==="gacha"&&<SectionFrame title="World Data" tabs={worldTabs} active={view} setView={setView}><Gachapon notify={notify} focusLocation={focusLocation} onOpen={inspectEntity}/></SectionFrame>}
        {view==="audit"&&<SectionFrame title="Audit & Tools" tabs={auditTabs} active={view} setView={setView}><Audit/></SectionFrame>}
  </ConsoleShell>
}

function SectionFrame({title,tabs,active,setView,children}:{title:string;tabs:readonly [View,string][];active:View;setView:(view:View)=>void;children:ReactNode}){
  return <div className="planned-workspace"><div className="planned-workspace-body">{children}</div></div>
}

function avatarEquipIdsWithUnderwear(items:any[],gender:number){
  const visible=items.filter(Boolean);
  const ids=visible.map(item=>Number(item.itemid)).filter(id=>Number.isFinite(id)&&id>0);
  const positions=new Set(visible.map(item=>Number(item.position)));
  const defaults=defaultUnderwearByGender[Number(gender)===1?1:0];
  if(!positions.has(-5)&&!positions.has(-105))ids.push(defaults.top);
  if(!positions.has(-6)&&!positions.has(-106))ids.push(defaults.bottom);
  return ids;
}

function AccountCharacterCreate({notify,onCreated,initialAccount,onInspect}:{notify:Notify;onCreated:(id:number)=>void;initialAccount?:any|null;onInspect:(account:any,characters:any[])=>void}){
  const [mode,setMode]=useState<"new"|"existing">("new");
  const [accounts,setAccounts]=useState<any[]>([]);
  const [accountQuery,setAccountQuery]=useState("");
  const [selectedAccount,setSelectedAccount]=useState<any|null>(null);
  const [accountName,setAccountName]=useState("");
  const [password,setPassword]=useState("");
  const [showPassword,setShowPassword]=useState(false);
  const [email,setEmail]=useState("");
  const [characterSlots,setCharacterSlots]=useState(3);
  const [ign,setIgn]=useState("");
  const [gm,setGm]=useState(0);
  const [nameStatus,setNameStatus]=useState<any|null>(null);
  const [busy,setBusy]=useState(false);
  const [appearance,setAppearance]=useState(defaultStarter);
  const loadAccounts=()=>api<Page<any>>(`/api/accounts?query=${encodeURIComponent(accountQuery)}&sort=name&direction=asc&page=0&size=80`).then(page=>setAccounts(page.items));
  useEffect(()=>{const timer=setTimeout(loadAccounts,160);return()=>clearTimeout(timer)},[accountQuery]);
  useEffect(()=>{if(!initialAccount)return;setMode("existing");inspectAccount(initialAccount);setAccountQuery(initialAccount.name||String(initialAccount.id||""))},[initialAccount?.id]);
  useEffect(()=>{if(!ign.trim()){setNameStatus(null);return}const timer=setTimeout(()=>api<any>(`/api/characters/name-check?name=${encodeURIComponent(ign)}`).then(setNameStatus).catch(error=>setNameStatus({valid:false,available:false,message:error.message})),180);return()=>clearTimeout(timer)},[ign]);
  const equips=[appearance.top,appearance.bottom,appearance.shoes,appearance.weapon].filter(Boolean);
  const canCreate=Boolean(ign.trim())&&nameStatus?.valid&&nameStatus?.available&&(mode==="existing"?selectedAccount:accountName.trim()&&password.trim());
  async function ensureAccount(){
    if(mode==="existing"){
      if(!selectedAccount)throw new Error("Choose an account first");
      return selectedAccount;
    }
    const account=await api<any>("/api/accounts",{method:"POST",body:JSON.stringify({name:accountName,password,email,gender:appearance.gender,characterSlots,reason:"Created through account creation page"})});
    setSelectedAccount(account);
    return account;
  }
  async function inspectAccount(account:any){
    setSelectedAccount(account);
    const characters=await api<any[]>(`/api/accounts/${account.id}/characters`);
    onInspect(account,characters);
  }
  async function createCharacter(){
    setBusy(true);
    try{
      const account=await ensureAccount();
      const character=await api<any>(`/api/accounts/${account.id}/characters`,{method:"POST",body:JSON.stringify({name:ign,world:0,gm,skincolor:appearance.skincolor,gender:appearance.gender,hair:appearance.hair,face:appearance.face,top:appearance.top,bottom:appearance.bottom,shoes:appearance.shoes,weapon:appearance.weapon,reason:"Created through account creation page"})});
      notify(mode==="new"?"Account and character created":"Character created");
      onCreated(Number(character.id));
    }finally{
      setBusy(false);
    }
  }
  return <div className="creation-workspace">
    <section className="panel creation-account-panel"><div className="creation-mode-tabs"><button className={mode==="new"?"active":""} onClick={()=>setMode("new")} type="button"><UserPlus size={16}/>New account</button><button className={mode==="existing"?"active":""} onClick={()=>setMode("existing")} type="button"><UsersRound size={16}/>Existing account</button></div>
    {mode==="new"?<div className="creation-form-grid"><label>Account name<input value={accountName} onChange={e=>setAccountName(e.target.value)} maxLength={13} placeholder="New login name"/></label><label className="password-field"><span>Password</span><div className="password-field-row"><input value={password} onChange={e=>setPassword(e.target.value)} type={showPassword?"text":"password"}/><button type="button" className="password-toggle" onClick={()=>setShowPassword(state=>!state)}>{showPassword?<EyeOff size={16}/>:<Eye size={16}/>}</button></div></label><label>Email<input value={email} onChange={e=>setEmail(e.target.value)} placeholder="Optional"/></label><NumberField label="Character slots" value={characterSlots} set={setCharacterSlots}/></div>
        :<><PageToolbar query={accountQuery} onQueryChange={setAccountQuery} placeholder="Search account, email, character name or ID"/><div className="creation-account-grid">{accounts.map(account=><button type="button" key={account.id} className={selectedAccount?.id===account.id?"selected":""} onClick={()=>inspectAccount(account)}><div className="account-orb">{String(account.name||"?").slice(0,2).toUpperCase()}</div><span><strong>{account.name}</strong><small>Account #{account.id} | {account.character_count}/{account.characterslots||3} characters</small><small>World {account.worlds||"-"} | GM {account.max_gm||0}</small></span></button>)}</div></>}
    </section>
    <section className="panel creation-character-panel"><PanelTitle title="Character" subtitle="Create a clean beginner character with starter appearance, gear, keymap and storage rows."/>
      <div className="creation-character-grid"><article className="creation-avatar-card"><img className="avatar-fullbody creation-avatar" src={avatarUrl({skinColor:appearance.skincolor,hair:appearance.hair,face:appearance.face,equips})} alt="New character avatar"/><strong>{ign||"NewCharacter"}</strong><small>Beginner | Maple Island start map 10000</small></article>
        <div className="creation-fields"><div className="creation-identity-row"><label className="ign-check-field">IGN<input value={ign} maxLength={13} onChange={e=>setIgn(e.target.value)} placeholder="Character name"/>{nameStatus&&<small className={nameStatus.available?"available":"unavailable"}>{nameStatus.message}</small>}</label><GmLevelSelect value={gm} set={setGm}/></div>
          <div className="creation-appearance-grid"><StarterAppearancePicker slot="hair" label="Hair" gender={appearance.gender} value={appearance.hair} set={itemId=>setAppearance(previous=>({...previous,hair:itemId}))}/><StarterAppearancePicker slot="face" label="Face" gender={appearance.gender} value={appearance.face} set={itemId=>setAppearance(previous=>({...previous,face:itemId}))}/><ChoiceSelect label="Skin" value={appearance.skincolor} choices={starterSkinChoices} onSelect={choice=>setAppearance(previous=>({...previous,skincolor:choice.id}))}/><GenderIconSelect value={appearance.gender} onSelect={value=>setAppearance(previous=>({...previous,gender:value}))}/></div>
        </div></div>
      <div className="starter-equip-section"><h3>Starter equipment</h3><div className="starter-equip-grid"><StarterEquipPicker slot="top" label="Top" gender={appearance.gender} value={appearance.top} set={itemId=>setAppearance(previous=>({...previous,top:itemId}))}/><StarterEquipPicker slot="bottom" label="Bottom" gender={appearance.gender} value={appearance.bottom} set={itemId=>setAppearance(previous=>({...previous,bottom:itemId}))}/><StarterEquipPicker slot="shoes" label="Shoes" gender={appearance.gender} value={appearance.shoes} set={itemId=>setAppearance(previous=>({...previous,shoes:itemId}))}/><StarterEquipPicker slot="weapon" label="Weapon" gender={appearance.gender} value={appearance.weapon} set={itemId=>setAppearance(previous=>({...previous,weapon:itemId}))}/></div></div>
      <div className="creation-actions"><button className="primary" disabled={!canCreate||busy} onClick={createCharacter} type="button"><UserPlus size={16}/>{busy?"Creating...":mode==="new"?"Create account and character":"Create character"}</button></div>
    </section>
  </div>
}

function sectionHeader(view:View){
  const groups:{title:string;tabs:readonly [View,string][]}[]=[
    {title:"Account",tabs:[["accounts","Account Search"],["create-account","Create"]]},
    {title:"Account > Character",tabs:characterTabs},
    {title:"Items",tabs:[["items","Catalog"]]},
    {title:"Mobs",tabs:[["mobs","Catalog / Drop Table"]]},
    {title:"World Data",tabs:worldTabs},
    {title:"Audit & Tools",tabs:auditTabs},
  ];
  for(const group of groups){const activeIndex=group.tabs.findIndex(([key])=>key===view);if(activeIndex>=0)return{...group,activeIndex,label:group.tabs[activeIndex][1]}}
  return null;
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
  const [usedOnly,setUsedOnly]=useState(fixedType==="ITEM");
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
    <button className="secondary icon-sort" title={direction==="asc"?"Ascending":"Descending"} aria-label={direction==="asc"?"Ascending":"Descending"} onClick={()=>setDirection(direction==="asc"?"desc":"asc")}><ArrowUpDown size={16}/></button></PageToolbar>
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

function Drops({notify,focusMob,onOpen}:{notify:Notify;focusMob?:number;onOpen:(type:string,id:number)=>void}){
  const [mode,setMode]=useState<"mob"|"global">("mob");const [mob,setMob]=useState<Entity|null>(null);const [rows,setRows]=useState<any[]>([]);
  const [regions,setRegions]=useState<any[]>([]);const [region,setRegion]=useState<any>();const [regionMobs,setRegionMobs]=useState<any[]>([]);const [mobPage,setMobPage]=useState(0);
  const [minLevel,setMinLevel]=useState("");const [maxLevel,setMaxLevel]=useState("");const [mobSort,setMobSort]=useState("level");const [mobDirection,setMobDirection]=useState<"asc"|"desc">("asc");
  const [deleteDrop,setDeleteDrop]=useState<any|null>(null);
  useEffect(()=>{api<any[]>("/api/catalog/regions").then(setRegions)},[]);
  async function chooseRegion(value:any){setRegion(value);setMob(null);setRows([]);setMobPage(0);setRegionMobs(await api<any[]>(`/api/catalog/regions/${value.region_code}/mobs`))}
  useEffect(()=>{if(focusMob){setMode("mob");api<any[]>(`/api/catalog/suggest?q=${focusMob}&type=MOB`).then(r=>r[0]&&setMob(r[0]))}},[focusMob]);
  async function load(){setRows(await api<any[]>(mode==="global"?"/api/global-drops":`/api/drops?mobId=${mob?.entity_id||0}`))}
  useEffect(()=>{if(mode==="global"||mob)void load();else setRows([])},[mode,mob]);
  async function patch(row:any,field:string,value:number){const body=mode==="global"
    ?{continent:row.continent,itemId:row.itemid,minimumQuantity:field==="minimum_quantity"?value:row.minimum_quantity,maximumQuantity:field==="maximum_quantity"?value:row.maximum_quantity,questId:field==="questid"?value:row.questid,chance:field==="chance"?value:row.chance,comments:row.comments||"",reason:`Inline ${field} update`}
    :{mobId:row.dropperid,itemId:row.itemid,minimumQuantity:field==="minimum_quantity"?value:row.minimum_quantity,maximumQuantity:field==="maximum_quantity"?value:row.maximum_quantity,questId:field==="questid"?value:row.questid,chance:field==="chance"?value:row.chance,reason:`Inline ${field} update`};
    await api(`${mode==="global"?"/api/global-drops":"/api/drops"}/${row.id}`,{method:"PUT",body:JSON.stringify(body)});notify("Drop updated");load()}
  async function remove(row:any){await api(`${mode==="global"?"/api/global-drops":"/api/drops"}/${row.id}?reason=Deleted%20from%20Console`,{method:"DELETE"});setDeleteDrop(null);notify("Drop deleted");load()}
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
      {region&&<div className="mob-picker"><div className="mob-picker-head"><h3>{region.region_name} monsters</h3><div className="compact-filters"><input type="number" min="0" placeholder="Min level" value={minLevel} onChange={e=>{setMinLevel(e.target.value);setMobPage(0)}}/><input type="number" min="0" placeholder="Max level" value={maxLevel} onChange={e=>{setMaxLevel(e.target.value);setMobPage(0)}}/><select value={mobSort} onChange={e=>{setMobSort(e.target.value);setMobPage(0)}}><option value="level">Level</option><option value="name">Name</option><option value="spawns">Spawn count</option></select><button className="secondary icon-sort" title={mobDirection==="asc"?"Ascending":"Descending"} aria-label={mobDirection==="asc"?"Ascending":"Descending"} onClick={()=>{setMobDirection(mobDirection==="asc"?"desc":"asc");setMobPage(0)}}><ArrowUpDown size={16}/></button></div><Pager page={mobPage} pages={Math.ceil(visibleMobs.length/24)} setPage={setMobPage}/></div>
        <div className="mob-picker-grid">{visibleMobs.slice(mobPage*24,mobPage*24+24).map(m=><button className={mob?.entity_id===m.entity_id?"selected":""} key={m.entity_id} onClick={()=>{setMob({entity_type:"MOB",entity_id:m.entity_id,name:m.name,level_value:m.level_value});onOpen("MOB",m.entity_id)}}>
        <EntityImage type="MOB" id={m.entity_id} properties={metadata(m.properties_json)}/><span><strong>{m.name}</strong><small>Lv. {m.level_value||0} | {m.spawn_count} spawns | {m.map_count} maps</small></span></button>)}</div><Pager page={mobPage} pages={Math.ceil(visibleMobs.length/24)} setPage={setMobPage}/></div>}</div></>}
    <div className="two-column wide-left drops-layout"><article className="panel"><PanelTitle title={mode==="global"?"Global drops":mob?`${mob.name} drops`:"Choose a monster"} subtitle={`${rows.length} entries. Click numeric values to edit.`}/>
      <div className="rich-list drop-list">{rows.map(row=><div className="rich-row drop-row" key={row.id}><button className="icon-link" onClick={()=>row.itemid>0&&onOpen("ITEM",row.itemid)}>{row.itemid===0?<MesoIcon min={row.minimum_quantity} max={row.maximum_quantity}/>:<img src={assetUrl("ITEM",row.itemid)} alt=""/>}</button>
        <button className="row-identity" onClick={()=>row.itemid>0&&onOpen("ITEM",row.itemid)}><strong>{row.itemid===0?"Meso drop":row.item_name||`Item ${row.itemid}`}</strong><code>{row.itemid===0?"Currency":row.itemid}</code></button>
        {mode==="global"&&<span className="tag soft">Continent {row.continent}</span>}
        <InlineNumber value={row.minimum_quantity} save={v=>patch(row,"minimum_quantity",v)} label="Min"/>
        <InlineNumber value={row.maximum_quantity} save={v=>patch(row,"maximum_quantity",v)} label="Max"/>
        <InlineNumber value={row.chance} save={v=>patch(row,"chance",v)} label="Chance"/>
        <InlineNumber value={row.questid||0} save={v=>patch(row,"questid",v)} label="Quest"/>
        <Chance value={row.chance}/><button className="danger-icon" onClick={()=>setDeleteDrop(row)} title="Delete drop"><Trash2 size={15}/></button></div>)}</div></article>
      <DropAdd mode={mode} mob={mob} after={()=>{notify("Drop added");load()}}/></div>
      {deleteDrop&&<ConfirmDialog title="Delete Drop" message={`Remove ${deleteDrop.itemid===0?"this meso drop":deleteDrop.item_name||`item ${deleteDrop.itemid}`} from ${mode==="global"?"global drops":mob?.name||"this monster"}?`} confirmLabel="Delete" onCancel={()=>setDeleteDrop(null)} onConfirm={()=>remove(deleteDrop)}/>}
    </>
}

function DropAdd({mode,mob,after}:{mode:"mob"|"global";mob:Entity|null;after:()=>void}){
  const [item,setItem]=useState<Entity|null>(null);const [chance,setChance]=useState(10000);
  async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();if(!item||mode==="mob"&&!mob)return;const f=new FormData(e.currentTarget);
    const body={mobId:mob?.entity_id,continent:Number(f.get("continent")||-1),itemId:item.entity_id,minimumQuantity:Number(f.get("min")),maximumQuantity:Number(f.get("max")),questId:Number(f.get("quest")||0),chance,comments:f.get("comments"),reason:"Added drop in Console"};
    await api(mode==="global"?"/api/global-drops":"/api/drops",{method:"POST",body:JSON.stringify(body)});setItem(null);after()}
  return <form className="panel editor-form drop-add-panel" onSubmit={submit}><PanelTitle title="Add drop" subtitle="Search catalog or add the monster's meso drop"/>
    <Autocomplete type="ITEM" value={item?.entity_id===0?null:item} onSelect={setItem} placeholder="Search item name or ID"/><button type="button" className="secondary" onClick={()=>setItem({entity_type:"ITEM",entity_id:0,name:"Meso drop",description:"Currency amount uses minimum and maximum quantity"})}><Coins/>Select meso drop</button>
    {item&&item.entity_id===0?<div className="selected-entity"><MesoIcon min={1} max={1}/><span><strong>Meso drop</strong><small>Minimum and maximum are meso amounts</small></span></div>:item&&<SelectedEntity entity={item}/>}
    {mode==="global"&&<label>Continent<input name="continent" type="number" defaultValue="-1"/></label>}
    <div className="form-row"><label>Minimum<input name="min" type="number" defaultValue="1"/></label><label>Maximum<input name="max" type="number" defaultValue="1"/></label></div>
    <label>Chance<input value={chance} onChange={e=>setChance(Number(e.target.value))} type="number" required/></label><Chance value={chance}/>
    <label>Quest ID <small>optional, 0 means always available</small><input name="quest" type="number" defaultValue="0"/></label>{mode==="global"&&<label>Comments<input name="comments"/></label>}
    <button className="primary" disabled={!item}>Add drop</button></form>
}

function Shops({notify,focusShop,onOpen}:{notify:Notify;focusShop?:number;onOpen:(type:string,id:number)=>void}){
  const [query,setQuery]=useState("");const [shops,setShops]=useState<any[]>([]);const [selected,setSelected]=useState<number|undefined>(focusShop);const [items,setItems]=useState<any[]>([]);const [page,setPage]=useState(0);
  const [deleteItem,setDeleteItem]=useState<any|null>(null);
  const loadShops=()=>api<any[]>(`/api/shops?query=${encodeURIComponent(query)}`).then(setShops);
  const loadItems=async()=>{if(selected)setItems(await api<any[]>(`/api/shops/${selected}/items`))};
  const orderedItems=useMemo(()=>[...items].sort((a,b)=>Number(b.position||0)-Number(a.position||0)),[items]);
  useEffect(()=>{setPage(0);const t=setTimeout(loadShops,180);return()=>clearTimeout(t)},[query]);useEffect(()=>{if(focusShop){setQuery("");setSelected(focusShop)}},[focusShop]);useEffect(()=>{if(selected)loadItems()},[selected]);
  useEffect(()=>{if(!selected||!shops.length)return;const index=shops.findIndex(shop=>Number(shop.shopid)===Number(selected));if(index>=0)setPage(Math.floor(index/12))},[shops,selected]);
  async function patch(row:any,field:string,value:number){await api(`/api/shops/${selected}/items/${row.shopitemid}`,{method:"PUT",body:JSON.stringify({itemId:row.itemid,price:field==="price"?value:row.price,pitch:row.pitch,position:field==="position"?value:row.position,reason:`Inline ${field} update`})});notify("Shop item updated");loadItems()}
  async function createShop(){const npc=prompt("NPC ID for the new shop");if(!npc)return;const result=await api<any>("/api/shops",{method:"POST",body:JSON.stringify({npcId:Number(npc),reason:"Created in Console"})});await loadShops();setSelected(Number(result.shopId))}
  async function addItem(item:Entity){if(!selected)return;const position=items.reduce((max,row)=>Math.max(max,Number(row.position||0)),100)+4;await api(`/api/shops/${selected}/items`,{method:"POST",body:JSON.stringify({itemId:item.entity_id,price:1,pitch:0,position,reason:"Added in Console"})});notify("Shop item added");loadItems()}
  async function remove(row:any){await api(`/api/shops/${selected}/items/${row.shopitemid}?reason=Deleted%20from%20Console`,{method:"DELETE"});setDeleteItem(null);notify("Shop item deleted");loadItems()}
  async function drag(row:any,target:any){if(row.shopitemid===target.shopitemid)return;await api(`/api/shops/${selected}/items/swap`,{method:"POST",body:JSON.stringify({firstItemId:row.shopitemid,secondItemId:target.shopitemid,reason:"Reordered in Console"})});notify("Shop order updated");await loadItems()}
  const selectedShop=shops.find(shop=>Number(shop.shopid)===Number(selected));
  const selectedShopTitle=selectedShop?`${selectedShop.npc_name||`NPC ${selectedShop.npcid}`}${selectedShop.primary_map_name?`: ${selectedShop.primary_map_name}`:""}`:selected?`Shop ${selected}`:"Choose a shop";
  const selectedShopSubtitle=selectedShop?`Shop ${selectedShop.shopid} | NPC ${selectedShop.npcid} | ${selectedShop.item_count} items. Items are shown by order from high to low.`:"Items are shown by order from high to low. Drag by the handle to reorder.";
  return <><PageToolbar query={query} onQueryChange={setQuery} placeholder="Search NPC, shop ID or NPC ID"><button className="primary" onClick={createShop}>New shop</button></PageToolbar>
    <div className="split-list"><div className="list-column"><Pager page={page} pages={Math.ceil(shops.length/12)} setPage={setPage}/><div className="list-panel">{shops.slice(page*12,page*12+12).map(s=><button key={s.shopid} className={Number(selected)===Number(s.shopid)?"selected":""} onClick={()=>setSelected(s.shopid)}>
      <EntityImage className="list-avatar" type="NPC" id={s.npcid}/><span><strong>{s.primary_map_name?`${s.npc_name||`NPC ${s.npcid}`}: ${s.primary_map_name}`:s.npc_name||`NPC ${s.npcid}`}</strong><small>Shop {s.shopid} | {s.item_count} items</small><small>{s.npcid}</small></span><ChevronRight size={16}/></button>)}</div><Pager page={page} pages={Math.ceil(shops.length/12)} setPage={setPage}/></div>
      <article className="panel"><PanelTitle title={selectedShopTitle} subtitle={selectedShopSubtitle}/>
        {selected&&<><Autocomplete type="ITEM" value={null} onSelect={addItem} placeholder="Search item to add"/>
          <div className="rich-list">{orderedItems.map(row=><div className="rich-row" key={row.shopitemid} onDragOver={e=>e.preventDefault()} onDrop={e=>drag(JSON.parse(e.dataTransfer.getData("row")),row)}>
            <button type="button" className="drag-handle" draggable onDragStart={e=>e.dataTransfer.setData("row",JSON.stringify(row))} title="Drag to reorder" aria-label="Drag to reorder"><GripVertical size={16}/></button>
            <button className="icon-link" onClick={()=>onOpen("ITEM",row.itemid)}><img src={assetUrl("ITEM",row.itemid)} alt=""/></button>
            <button className="row-identity" onClick={()=>onOpen("ITEM",row.itemid)}><strong>{row.item_name||row.itemid}</strong><code>{row.itemid}</code></button>
            <InlineNumber value={row.position} save={v=>patch(row,"position",v)} label="Position"/><InlineNumber value={row.price} save={v=>patch(row,"price",v)} label="Price"/>
            <button className="danger-icon" onClick={()=>setDeleteItem(row)}><Trash2 size={15}/></button></div>)}</div></>}</article></div>
    {deleteItem&&<ConfirmDialog title="Delete shop item?" message={`${deleteItem.item_name||`Item ${deleteItem.itemid}`} will be removed from this NPC shop. This cannot be undone.`} confirmLabel="Delete item" onCancel={()=>setDeleteItem(null)} onConfirm={()=>remove(deleteItem)}/>}</>
}

function Gachapon({notify,focusLocation,onOpen}:{notify:Notify;focusLocation?:string;onOpen:(type:string,id:number)=>void}){
  const [locations,setLocations]=useState<any[]>([]);const [selected,setSelected]=useState<string>();const [rows,setRows]=useState<any[]>([]);const [page,setPage]=useState(0);const [query,setQuery]=useState("");
  const [pendingReward,setPendingReward]=useState<Entity|null>(null);const [deleteReward,setDeleteReward]=useState<any|null>(null);
  const loadLocations=()=>api<any[]>("/api/gachapon").then(setLocations);const load=async()=>{if(selected)setRows(await api<any[]>(`/api/gachapon/${selected}`))};
  const tierCounts=useMemo(()=>rows.reduce((counts,row)=>{if(row.enabled===false)return counts;const tier=Number(row.tier||0);counts[tier]=(counts[tier]||0)+1;return counts},{} as Record<number,number>),[rows]);
  const gachaChance=(row:any)=>gachaponChanceDisplay(row,tierCounts[Number(row.tier||0)]);
  useEffect(()=>{loadLocations()},[]);useEffect(()=>{if(focusLocation){setQuery("");setSelected(focusLocation)}},[focusLocation]);useEffect(()=>{load()},[selected]);
  useEffect(()=>{if(!selected||!locations.length)return;const index=locations.findIndex(location=>location.location_code===selected);if(index>=0)setPage(Math.floor(index/8))},[locations,selected]);
  async function add(item:Entity,tier:number){if(!selected)return;await api(`/api/gachapon/${selected}`,{method:"POST",body:JSON.stringify({tier,itemId:item.entity_id,npcId:null,enabled:true,reason:"Added in Console"})});setPendingReward(null);notify("Gachapon reward added");load()}
  async function patchTier(row:any,tier:number){if(!selected||Number(row.tier)===tier)return;await api(`/api/gachapon/${selected}/${row.id}`,{method:"PUT",body:JSON.stringify({tier,itemId:row.item_id,npcId:row.npc_id??null,enabled:row.enabled!==false,reason:"Updated rarity in Console"})});notify("Gachapon rarity updated");load()}
  async function remove(row:any){if(!selected)return;await api(`/api/gachapon/${selected}/${row.id}?reason=Deleted%20from%20Console`,{method:"DELETE"});setDeleteReward(null);notify("Gachapon reward deleted");load()}
  const filteredLocations=locations.filter(x=>!query||gachaponTown(x.location_code).toLowerCase().includes(query.toLowerCase())||String(x.npc_id||"").includes(query));
  return <><PageToolbar query={query} onQueryChange={v=>{setQuery(v);setPage(0)}} placeholder="Search gachapon town or NPC ID"/><div className="source-banner"><Ticket className="gacha-ticket-icon" size={18}/><div><strong>Live database override</strong><span>The game server reads these rows first and falls back to the original Java reward arrays when a location has no Console entries.</span></div></div>
    <div className="split-list"><div className="list-column"><Pager page={page} pages={Math.ceil(filteredLocations.length/8)} setPage={setPage}/><div className="list-panel">{filteredLocations.slice(page*8,page*8+8).map(x=><button key={x.location_code} className={selected===x.location_code?"selected":""} onClick={()=>setSelected(x.location_code)}>
      {x.npc_id?<EntityImage className="list-avatar" type="NPC" id={x.npc_id}/>:<Ticket className="list-avatar gacha-ticket-icon"/>}<span><strong>{x.location_code==="GLOBAL"?"Global Gachapon":`Gachapon: ${gachaponTown(x.location_code)}`}</strong><small>{x.item_count} rewards | {x.region_name||"Global pool"}</small><small>{x.npc_id||"GLOBAL"}</small></span><ChevronRight size={16}/></button>)}</div><Pager page={page} pages={Math.ceil(filteredLocations.length/8)} setPage={setPage}/></div>
      <article className="panel"><PanelTitle title={selected?(selected==="GLOBAL"?"Global Gachapon":`Gachapon: ${gachaponTown(selected)}`):"Choose a gachapon"} subtitle="Common 90%, uncommon 8%, rare 2% before global-pool mixing"/>
        {selected&&<><Autocomplete type="ITEM" value={null} onSelect={setPendingReward} placeholder="Search reward to add"/>
          <div className="rich-list">{rows.map(row=><div className="rich-row gacha-row" key={row.id}><img src={assetUrl("ITEM",row.item_id)} alt=""/><button className="row-identity" onClick={()=>onOpen("ITEM",row.item_id)}><strong>{row.item_name||row.item_id}</strong><code>{row.item_id}</code></button>
            <select className={`tier-select tier-${row.tier}`} value={Number(row.tier||0)} onChange={event=>patchTier(row,Number(event.target.value))}><option value={0}>Common</option><option value={1}>Uncommon</option><option value={2}>Rare</option></select><span className="gacha-chance"><strong>{gachaChance(row).percent}</strong><small>{gachaChance(row).one}</small></span><small className="source-line">{row.source_kind}</small><button className="danger-icon" onClick={()=>setDeleteReward(row)}><Trash2 size={15}/></button></div>)}</div></>}</article></div>
    {pendingReward&&<RarityDialog item={pendingReward} onCancel={()=>setPendingReward(null)} onConfirm={tier=>add(pendingReward,tier)}/>}
    {deleteReward&&<ConfirmDialog title="Delete Gachapon reward?" message={`${deleteReward.item_name||`Item ${deleteReward.item_id}`} will be removed from ${selected==="GLOBAL"?"Global Gachapon":`Gachapon: ${gachaponTown(selected||"")}`}. This cannot be undone.`} confirmLabel="Delete reward" onCancel={()=>setDeleteReward(null)} onConfirm={()=>remove(deleteReward)}/>}</>
}

function Accounts({notify,onInspect,onCreateCharacter}:{notify:Notify;onInspect:(account:any,characters:any[])=>void;onCreateCharacter:(account:any)=>void}){
  const [query,setQuery]=useState("");const [sort,setSort]=useState("lastlogin");const [direction,setDirection]=useState("desc");const [page,setPage]=useState(0);
  const [worldFilter,setWorldFilter]=useState("all");const [statusFilter,setStatusFilter]=useState("all");const [gmFilter,setGmFilter]=useState("all");
  const [data,setData]=useState<Page<any>>({items:[],page:0,size:50,total:0,pages:0});const [selected,setSelected]=useState<any>();
  const load=()=>api<Page<any>>(`/api/accounts?query=${encodeURIComponent(query)}&sort=${sort}&direction=${direction}&page=${page}&size=50`).then(setData);
  useEffect(()=>{const t=setTimeout(load,180);return()=>clearTimeout(t)},[query,sort,direction,page]);
  async function choose(row:any){const accountCharacters=await api<any[]>(`/api/accounts/${row.id}/characters`);setSelected(row);onInspect(row,accountCharacters)}
  const worlds=useMemo(()=>Array.from(new Set(data.items.flatMap(row=>String(row.worlds||"").split(",").filter(Boolean)))).sort((a,b)=>Number(a)-Number(b)),[data.items]);
  const gmLevels=useMemo(()=>Array.from(new Set(data.items.map(row=>String(row.max_gm||0)))).sort((a,b)=>Number(a)-Number(b)),[data.items]);
  const filtered=data.items.filter(row=>{
    const status=row.banned?"banned":row.loggedin?"online":row.mute?"muted":"active";
    const rowWorlds=String(row.worlds||"").split(",").filter(Boolean);
    return (worldFilter==="all"||rowWorlds.includes(worldFilter))
      && (statusFilter==="all"||status===statusFilter||(statusFilter==="offline"&&!row.loggedin))
      && (gmFilter==="all"||String(row.max_gm||0)===gmFilter);
  });
  return <><PageToolbar query={query} onQueryChange={value=>{setQuery(value);setPage(0)}} placeholder="Account, email, character name or ID">
    <select value={worldFilter} onChange={e=>setWorldFilter(e.target.value)}><option value="all">All worlds</option>{worlds.map(world=><option value={world} key={world}>World {world}</option>)}</select>
    <select value={statusFilter} onChange={e=>setStatusFilter(e.target.value)}><option value="all">All status</option><option value="active">Active</option><option value="online">Online</option><option value="offline">Offline</option><option value="muted">Muted</option><option value="banned">Banned</option></select>
    <select value={gmFilter} onChange={e=>setGmFilter(e.target.value)}><option value="all">All GM levels</option>{gmLevels.map(gm=><option value={gm} key={gm}>GM {gm}</option>)}</select>
    <select value={sort} onChange={e=>setSort(e.target.value)}><option value="lastlogin">Last login</option><option value="id">Account ID</option><option value="name">Account name</option><option value="created">Created date</option><option value="characters">Character count</option></select>
    <button className="secondary icon-sort" title={direction==="asc"?"Ascending":"Descending"} aria-label={direction==="asc"?"Ascending":"Descending"} onClick={()=>setDirection(direction==="asc"?"desc":"asc")}><ArrowUpDown size={16}/></button></PageToolbar>
    <div className="account-page-grid"><section className="account-results"><div className="results-bar"><span>{filtered.length} accounts on this page | {data.total} total</span><span>Click an account to edit in the dock</span></div><Pager page={page} pages={data.pages} setPage={setPage}/>
      <div className="account-card-grid">{filtered.map(row=>{const status=row.banned?"Banned":row.loggedin?"Online":row.mute?"Muted":"Active";const count=Number(row.character_count||0);const slots=Number(row.characterslots||3);return <article key={row.id} className={`account-card ${selected?.id===row.id?"selected":""}`} role="button" tabIndex={0} onClick={()=>choose(row)} onKeyDown={event=>{if(event.key==="Enter"||event.key===" "){event.preventDefault();choose(row)}}}>
        <div className="account-card-top"><div className="account-orb">{String(row.name||"?").slice(0,2).toUpperCase()}</div><div><strong>{row.name}</strong><code>Account #{row.id}</code></div></div>
        <div className="account-card-meta"><span>World {row.worlds||"-"}</span><span className={row.banned?"danger-tag":"tag soft"}>{status}</span><span>GM {row.max_gm||0}</span><span>{count}/{slots} characters</span></div>
        <p>{row.character_names||"No characters created"}</p>
        <div className="account-card-actions"><button type="button" onClick={event=>{event.stopPropagation();onCreateCharacter(row)}}><UserPlus size={14}/>Create character</button></div>
      </article>})}</div><Pager page={page} pages={data.pages} setPage={setPage}/></section></div></>
}

function AccountDrawer({account,characters,close,notify,onCharacter}:{account:any;characters:any[];close:()=>void;notify:Notify;onCharacter:(view:View,id:number)=>void}){
  const [selected,setSelected]=useState(account);
  useEffect(()=>setSelected(account),[account]);
  async function saveAccount(field:string,value:any){const row={...selected,[field]:value};const updated=await api<any>(`/api/accounts/${row.id}`,{method:"PATCH",body:JSON.stringify({banned:!!row.banned,banReason:row.banreason||"",mute:!!row.mute,nxCredit:Number(row.nxCredit||0),maplePoint:Number(row.maplePoint||0),nxPrepaid:Number(row.nxPrepaid||0),characterSlots:Number(row.characterslots||3),reason:`Inline ${field} update`})});setSelected({...selected,...updated});notify("Account updated")}
  return <aside className="drawer account-detail-dock"><button type="button" className="modal-close" onClick={close}><X/></button>
    <div className="drawer-hero account-hero"><div className="account-orb account-orb-large">{String(selected.name||"?").slice(0,2).toUpperCase()}</div><div>
      <div className="tag-row"><span className="tag">ACCOUNT</span><span className={selected.banned?"danger-tag":"tag used"}>{selected.banned?"Banned":"Active"}</span>{selected.mute&&<span className="tag soft">Muted</span>}<span className="tag soft">GM {selected.max_gm||0}</span></div>
      <h2>{selected.name}</h2><code>{selected.id}</code><p>{selected.email||"No email"} | {selected.loggedin?"Online":"Offline"} | worlds {selected.worlds||"-"}</p>
    </div></div>
    <section><h3>Account Fields</h3><div className="field-grid"><EditableField label="NX credit" value={selected.nxCredit||0} save={v=>saveAccount("nxCredit",v)}/><EditableField label="Maple points" value={selected.maplePoint||0} save={v=>saveAccount("maplePoint",v)}/>
      <EditableField label="Prepaid NX" value={selected.nxPrepaid||0} save={v=>saveAccount("nxPrepaid",v)}/><EditableField label="Character slots" value={selected.characterslots||3} save={v=>saveAccount("characterslots",v)}/>
      <ToggleField label="Banned" value={!!selected.banned} save={v=>saveAccount("banned",v)}/><ToggleField label="Muted" value={!!selected.mute} save={v=>saveAccount("mute",v)}/></div></section>
    <section><h3>Characters <small>{selected.character_count||0}/{selected.characterslots||3}</small></h3><div className="character-dock-list">{characters.map(c=><CharacterDockCard key={c.id} character={c} onNavigate={onCharacter}/>)}</div></section>
    <section className="technical"><h3>Technical provenance</h3><PropertyGrid data={{id:selected.id,email:selected.email,loggedin:selected.loggedin,lastlogin:selected.lastlogin,createdat:selected.createdat,banreason:selected.banreason,worlds:selected.worlds,max_gm:selected.max_gm,character_count:selected.character_count,characterslots:selected.characterslots}}/>
      <code className="source-code">SQL: accounts WHERE id={selected.id}</code><code className="source-code">Linked SQL: characters WHERE accountid={selected.id}</code></section>
  </aside>
}

function CharacterStats({notify,focusCharacter,onCharacterSelect}:{notify:Notify;focusCharacter?:number;onCharacterSelect?:(id:number)=>void}){
  const [character,setCharacter]=useState<any|null>(null);const [form,setForm]=useState<any>({});
  const [accounts,setAccounts]=useState<any[]>([]);const [browseAccount,setBrowseAccount]=useState<any>();const [accountCharacters,setAccountCharacters]=useState<any[]>([]);
  const [accountQuery,setAccountQuery]=useState("");const [worldFilter,setWorldFilter]=useState("all");const [job,setJob]=useState<any|null>(null);const [map,setMap]=useState<Entity|null>(null);
  const [skills,setSkills]=useState<any[]>([]);const [equippedItems,setEquippedItems]=useState<any[]>([]);
  useEffect(()=>{api<Page<any>>("/api/accounts?sort=name&direction=asc&page=0&size=200").then(r=>setAccounts(r.items))},[]);
  useEffect(()=>{if(focusCharacter)api<any[]>(`/api/characters/search?query=${focusCharacter}`).then(r=>r[0]&&chooseCharacter(r[0]))},[focusCharacter]);
  async function chooseAccount(account:any){setBrowseAccount(account);setAccountCharacters(await api<any[]>(`/api/accounts/${account.id}/characters`))}
  async function chooseCharacter(row:any){const id=Number(row.id);const [details,skillRows,inventoryRows]=await Promise.all([api<any>(`/api/characters/${id}`),api<any[]>(`/api/characters/${id}/skills`),api<any[]>(`/api/characters/${id}/inventory`)]);setCharacter(details);setForm(details);setJob(details.job?{job_id:details.job,job_name:details.job_name||`Job ${details.job}`}:null);setMap(details.map?{entity_type:"MAP",entity_id:details.map,name:details.map_name||`Map ${details.map}`}:null);setSkills(skillRows.map(skill=>({...skill,savedSkilllevel:skill.skilllevel})));setEquippedItems(inventoryRows.filter(item=>Number(item.inventorytype)===1&&Number(item.position)<0));onCharacterSelect?.(id)}
  useEffect(()=>{if(!character?.accountid||!accounts.length)return;const account=accounts.find(row=>Number(row.id)===Number(character.accountid));if(account&&Number(browseAccount?.id)!==Number(account.id)){setBrowseAccount(account);api<any[]>(`/api/accounts/${account.id}/characters`).then(setAccountCharacters)}},[character?.accountid,accounts.length]);
  const accountWorlds=useMemo(()=>Array.from(new Set(accounts.flatMap(row=>String(row.worlds||"").split(",").filter(Boolean)))).sort((a,b)=>Number(a)-Number(b)),[accounts]);
  const visibleAccounts=accounts.filter(row=>{const rowWorlds=String(row.worlds||"").split(",").filter(Boolean);const haystack=`${row.name||""} ${row.id||""} ${row.character_names||""}`.toLowerCase();return (!accountQuery||haystack.includes(accountQuery.toLowerCase()))&&(worldFilter==="all"||rowWorlds.includes(worldFilter))});
  const visibleCharacters=accountCharacters.filter(c=>worldFilter==="all"||String(c.world)===worldFilter);
  const spPools=spArray(form.sp);
  const hpAverage=averageHpMp(num(form.level),num(job?.job_id??form.job),"hp");
  const mpAverage=averageHpMp(num(form.level),num(job?.job_id??form.job),"mp");
  const equipHpBonus=equippedItems.reduce((sum,item)=>sum+num(item.hp),0);
  const equipMpBonus=equippedItems.reduce((sum,item)=>sum+num(item.mp),0);
  function updateField(name:string,value:any){setForm((previous:any)=>({...previous,[name]:value}))}
  async function saveCharacter(nextForm:any=form,nextJob:any=job,nextMap:any=map,reason="Edited through AP/SP and stats page"){if(!character)return;const payload={level:num(nextForm.level),job:num(nextJob?.job_id??nextForm.job),gm:num(nextForm.gm),str:num(nextForm.str),dex:num(nextForm.dex),intStat:num(nextForm.int),luk:num(nextForm.luk),hp:num(nextForm.hp),mp:num(nextForm.mp),maxHp:num(nextForm.maxhp),maxMp:num(nextForm.maxmp),ap:num(nextForm.ap),sp:spArray(nextForm.sp).join(","),meso:num(nextForm.meso),fame:num(nextForm.fame),map:num(nextMap?.entity_id??nextForm.map),reason};const updated=await api<any>(`/api/characters/${character.id}`,{method:"PATCH",body:JSON.stringify(payload)});setCharacter(updated);setForm(updated);notify("Character updated")}
  async function saveStatField(name:string,value:number){if(num(character?.[name])===num(value))return;const next={...form,[name]:value};setForm(next);await saveCharacter(next,job,map,`Updated ${name} through AP/SP page`)}
  async function allocateAllAp(stat:"str"|"dex"|"int"|"luk"){const ap=num(form.ap);if(ap<=0)return;const next={...form,[stat]:num(form[stat])+ap,ap:0};setForm(next);await saveCharacter(next,job,map,`Allocated all AP into ${stat.toUpperCase()}`)}
  async function resetStatAp(stat:"str"|"dex"|"int"|"luk"){const refund=Math.max(0,num(form[stat])-4);const next={...form,[stat]:4,ap:num(form.ap)+refund};setForm(next);await saveCharacter(next,job,map,`Reset ${stat.toUpperCase()} AP back to unused AP`)}
  async function resetAp(){const total=totalApBudget(num(form.level));const next={...form,str:4,dex:4,int:4,luk:4,ap:Math.max(0,total-16)};setForm(next);await saveCharacter(next,job,map,"Reset all stat AP back to unused AP")}
  async function resetHpMp(kind:"hp"|"mp"){const key=kind==="hp"?"maxhp":"maxmp";const current=kind==="hp"?"hp":"mp";const target=averageHpMp(num(form.level),num(job?.job_id??form.job),kind);const next={...form,[key]:target,[current]:Math.min(num(form[current]),target)};setForm(next);await saveCharacter(next,job,map,`Reset average ${kind.toUpperCase()} by level/job`)}
  async function maxHpMp(kind:"hp"|"mp"){const key=kind==="hp"?"maxhp":"maxmp";const current=kind==="hp"?"hp":"mp";const next={...form,[key]:MAX_HP_MP,[current]:MAX_HP_MP};setForm(next);await saveCharacter(next,job,map,`Maxed ${kind.toUpperCase()} through AP/SP page`)}
  async function fullHpMp(kind:"hp"|"mp"){const key=kind==="hp"?"hp":"mp";const maxKey=kind==="hp"?"maxhp":"maxmp";const equipBonus=kind==="hp"?equipHpBonus:equipMpBonus;const next={...form,[key]:num(form[maxKey])+equipBonus};setForm(next);await saveCharacter(next,job,map,`Restored current ${kind.toUpperCase()} to effective max`)}
  async function saveSp(nextPools:number[]){if(!character)return;const sp=nextPools.map(value=>Math.max(0,num(value))).join(",");setForm((previous:any)=>({...previous,sp}));const updated=await api<any>(`/api/characters/${character.id}`,{method:"PATCH",body:JSON.stringify({level:num(form.level),job:num(job?.job_id??form.job),gm:num(form.gm),str:num(form.str),dex:num(form.dex),intStat:num(form.int),luk:num(form.luk),hp:num(form.hp),mp:num(form.mp),maxHp:num(form.maxhp),maxMp:num(form.maxmp),ap:num(form.ap),sp,meso:num(form.meso),fame:num(form.fame),map:num(map?.entity_id??form.map),reason:"Updated remaining SP through skill editor"})});setCharacter(updated);setForm((previous:any)=>({...previous,sp:updated.sp}))}
  async function setSpPool(tier:number,value:number){const pools=spArray(form.sp);pools[tier]=Math.max(0,num(value));await saveSp(pools);notify("Unused SP updated")}
  async function saveSkill(skill:any,level:number,reason:string){if(!character)return;await api(`/api/characters/${character.id}/skills/${skill.skillid}`,{method:"PATCH",body:JSON.stringify({skillLevel:level,masterLevel:Math.max(Number(skill.masterlevel||0),level),expiration:Number(skill.expiration??-1),reason})})}
  async function setSkillLevel(skill:any,next:number,enforcePool=true){if(!character)return;const book=skillBook(skill.job_id);const pools=spArray(form.sp);const max=Number(skill.max_level||0);const current=Number((skill.savedSkilllevel??skill.skilllevel)||0);const desired=Math.max(0,Math.min(max,next));const delta=desired-current;const adjusted=enforcePool&&delta>0?Math.min(current+pools[book],desired):desired;const finalDelta=adjusted-current;if(finalDelta===0){setSkills(rows=>rows.map(row=>row.skillid===skill.skillid?{...row,skilllevel:current}:row));return}pools[book]=Math.max(0,pools[book]-finalDelta);setForm((previous:any)=>({...previous,sp:pools.join(",")}));setSkills(rows=>rows.map(row=>row.skillid===skill.skillid?{...row,skilllevel:adjusted,savedSkilllevel:adjusted,masterlevel:Math.max(Number(row.masterlevel||0),adjusted)}:row));await saveSkill(skill,adjusted,enforcePool?"Edited skill level from AP/SP buttons":"Manually edited skill level from AP/SP page");await saveSp(pools);notify("Skill updated")}
  async function resetJobSkills(jobId:number){if(!character)return;const jobSkills=skills.filter(skill=>Number(skill.job_id||0)===jobId);const book=skillBook(jobId);const pools=spArray(form.sp);const refunded=jobSkills.reduce((sum,skill)=>sum+Number(skill.savedSkilllevel??skill.skilllevel??0),0);pools[book]+=refunded;setSkills(rows=>rows.map(row=>Number(row.job_id||0)===jobId?{...row,skilllevel:0,savedSkilllevel:0}:row));setForm((previous:any)=>({...previous,sp:pools.join(",")}));await Promise.all(jobSkills.map(skill=>saveSkill(skill,0,`Reset job ${jobId} skills from AP/SP page`)));await saveSp(pools);notify(`Reset job ${jobId} skills`)}
  async function resetTierSkills(tier:number){if(!character)return;const tierSkills=skills.filter(skill=>skillBook(skill.job_id)===tier);const pools=spArray(form.sp);const refunded=tierSkills.reduce((sum,skill)=>sum+Number(skill.savedSkilllevel??skill.skilllevel??0),0);pools[tier]+=refunded;setSkills(rows=>rows.map(row=>skillBook(row.job_id)===tier?{...row,skilllevel:0,savedSkilllevel:0}:row));setForm((previous:any)=>({...previous,sp:pools.join(",")}));await Promise.all(tierSkills.map(skill=>saveSkill(skill,0,`Reset ${["Beginner","1st job","2nd job","3rd job","4th job"][tier]} skills from AP/SP page`)));await saveSp(pools);notify(`${["Beginner","1st","2nd","3rd","4th"][tier]} job skills reset`)}
  async function maxTierSkills(tier:number){if(!character)return;const tierSkills=skills.filter(skill=>skillBook(skill.job_id)===tier);const pools=spArray(form.sp);pools[tier]=0;setSkills(rows=>rows.map(row=>skillBook(row.job_id)===tier?{...row,skilllevel:Number(row.max_level||0),savedSkilllevel:Number(row.max_level||0),masterlevel:Math.max(Number(row.masterlevel||0),Number(row.max_level||0))}:row));setForm((previous:any)=>({...previous,sp:pools.join(",")}));await Promise.all(tierSkills.map(skill=>saveSkill(skill,Number(skill.max_level||0),`Maxed ${["Beginner","1st job","2nd job","3rd job","4th job"][tier]} skills from AP/SP page`)));await saveSp(pools);notify(`${["Beginner","1st","2nd","3rd","4th"][tier]} job skills maxed`)}
  const columns=[0,1,2,3,4].map(tier=>({tier,label:["Beginner","1st Job","2nd Job","3rd Job","4th Job"][tier],jobs:Array.from(new Set(skills.filter(s=>skillBook(s.job_id)===tier).map(s=>Number(s.job_id||0)))).sort((a,b)=>a-b)}));
  return <><div className="inventory-account-toolbar"><SearchInput value={accountQuery} setValue={setAccountQuery} placeholder="Search account, character or ID"/><select value={worldFilter} onChange={e=>setWorldFilter(e.target.value)}><option value="all">All worlds</option>{accountWorlds.map(world=><option value={world} key={world}>World {world}</option>)}</select></div>
    <div className="inventory-browser"><div><h3>Browse accounts</h3>{visibleAccounts.map(a=><button className={browseAccount?.id===a.id?"selected":""} key={a.id} onClick={()=>chooseAccount(a)}><UsersRound/><span><strong>{a.name}</strong><small>World {a.worlds||"-"} | {a.banned?"Banned":a.loggedin?"Online":a.mute?"Muted":"Active"} | GM {a.max_gm||0}</small><small>{a.character_count}/{a.characterslots||3} characters</small></span></button>)}</div>
      <div><h3>{browseAccount?`${browseAccount.name}'s characters`:"Choose an account"}</h3>{visibleCharacters.map(c=><button key={c.id} className={Number(character?.id)===Number(c.id)?"selected":""} onClick={()=>chooseCharacter({...c,accountid:browseAccount.id,account_name:browseAccount.name})}><CircleUserRound/><span><strong>{browseAccount.name} → {c.name}</strong><small>World {c.world} | Lv. {c.level} {c.job_name||"Unknown job"} ({c.job}) | GM {c.gm||0}</small></span></button>)}</div></div>
    {!character?<Empty text="Choose an account and character to edit AP, stats, location, and skills"/>:<div className="panel ap-sp-panel"><PanelTitle title={`${character.account_name} → ${character.name}`} subtitle="Account must be offline before character stat or skill edits are saved"/>
      <div className="stat-editor-grid"><NumberField label="Level" value={form.level} set={v=>updateField("level",v)} onCommit={v=>saveStatField("level",v)}/><label className="selector-field">Job <JobAutocomplete value={job} onSelect={value=>{if(num(value.job_id)===num(job?.job_id??form.job))return;setJob(value);const next={...form,job:value.job_id};setForm(next);saveCharacter(next,value,map,"Updated job through AP/SP page")}}/></label>
        <NumberField label="STR" value={form.str} set={v=>updateField("str",v)} onCommit={v=>saveStatField("str",v)} leftAction={<button type="button" className="text-button compact" onClick={()=>allocateAllAp("str")}>All AP</button>} rightAction={<button type="button" className="text-button compact" onClick={()=>resetStatAp("str")}>Reset</button>}/><NumberField label="DEX" value={form.dex} set={v=>updateField("dex",v)} onCommit={v=>saveStatField("dex",v)} leftAction={<button type="button" className="text-button compact" onClick={()=>allocateAllAp("dex")}>All AP</button>} rightAction={<button type="button" className="text-button compact" onClick={()=>resetStatAp("dex")}>Reset</button>}/><NumberField label="INT" value={form.int} set={v=>updateField("int",v)} onCommit={v=>saveStatField("int",v)} leftAction={<button type="button" className="text-button compact" onClick={()=>allocateAllAp("int")}>All AP</button>} rightAction={<button type="button" className="text-button compact" onClick={()=>resetStatAp("int")}>Reset</button>}/><NumberField label="LUK" value={form.luk} set={v=>updateField("luk",v)} onCommit={v=>saveStatField("luk",v)} leftAction={<button type="button" className="text-button compact" onClick={()=>allocateAllAp("luk")}>All AP</button>} rightAction={<button type="button" className="text-button compact" onClick={()=>resetStatAp("luk")}>Reset</button>}/><NumberField label="Unused AP" value={form.ap} set={v=>updateField("ap",v)} onCommit={v=>saveStatField("ap",v)} rightAction={<button type="button" className="text-button compact" onClick={resetAp}>Reset All</button>}/>
        <NumberField className="stat-row-break" label="Current HP" value={form.hp} set={v=>updateField("hp",v)} onCommit={v=>saveStatField("hp",v)} leftAction={<button type="button" className="text-button compact" onClick={()=>fullHpMp("hp")}>Full</button>}/><NumberField label="Current MP" value={form.mp} set={v=>updateField("mp",v)} onCommit={v=>saveStatField("mp",v)} leftAction={<button type="button" className="text-button compact" onClick={()=>fullHpMp("mp")}>Full</button>}/><NumberField label="Max HP" value={form.maxhp} set={v=>updateField("maxhp",v)} onCommit={v=>saveStatField("maxhp",v)} detail={hpMpDetail(hpAverage,num(form.maxhp),equipHpBonus)} leftAction={<button type="button" className="text-button compact" onClick={()=>maxHpMp("hp")}>Max HP</button>} rightAction={<button type="button" className="text-button compact" onClick={()=>resetHpMp("hp")}>Reset</button>}/><NumberField label="Max MP" value={form.maxmp} set={v=>updateField("maxmp",v)} onCommit={v=>saveStatField("maxmp",v)} detail={hpMpDetail(mpAverage,num(form.maxmp),equipMpBonus)} leftAction={<button type="button" className="text-button compact" onClick={()=>maxHpMp("mp")}>Max MP</button>} rightAction={<button type="button" className="text-button compact" onClick={()=>resetHpMp("mp")}>Reset</button>}/><NumberField label="Fame" value={form.fame} set={v=>updateField("fame",v)} onCommit={v=>saveStatField("fame",v)}/><GmLevelSelect value={form.gm} set={v=>updateField("gm",v)} onCommit={v=>saveStatField("gm",v)}/><label className="selector-field map-field">Map<Autocomplete type="MAP" value={map} onSelect={value=>{if(num(value.entity_id)===num(map?.entity_id??form.map))return;setMap(value);const next={...form,map:value.entity_id};setForm(next);saveCharacter(next,job,value,"Updated map through AP/SP page")}} placeholder="Search map by ID or name"/></label></div>
      <section className="skill-board"><div className="skill-board-head"><div><h3>SP allocation and skills</h3><p>Use + or - to spend from or return to the matching SP pool. Typing a skill level is an admin override.</p></div></div>
        {skills.length===0?<div className="skill-empty-state"><strong>No skill rows found for this character yet.</strong><span>Import or refresh the WZ catalog from the dashboard. If this character already has saved skills, restart the API so the fallback saved-skill lookup is active.</span></div>
          :<div className="skill-columns">{columns.map(column=><article className="skill-column" key={column.tier}><div className="skill-column-head"><div className="skill-column-top"><strong>{column.label}</strong><label className="unused-sp-editor"><input type="number" min="0" value={spPools[column.tier]||0} onChange={e=>{const pools=spArray(form.sp);pools[column.tier]=Math.max(0,Number(e.target.value));setForm((previous:any)=>({...previous,sp:pools.join(",")}))}} onBlur={e=>setSpPool(column.tier,Number(e.target.value))} onKeyDown={e=>{if(e.key==="Enter"){e.preventDefault();e.currentTarget.blur()}}}/><span>unused SP</span></label></div><div className="skill-tier-actions"><button type="button" className="text-button compact" onClick={()=>maxTierSkills(column.tier)}>Max all</button><button type="button" className="text-button compact" onClick={()=>resetTierSkills(column.tier)}>Reset all</button></div></div>{column.jobs.map(jobId=>{const jobSkills=skills.filter(s=>Number(s.job_id||0)===jobId);const firstSkill=jobSkills[0];return <div className="skill-job-card" key={jobId}><h4><span><JobBadge jobId={jobId} skillId={Number(firstSkill?.skillid||0)}/>{firstSkill?.job_name||`Job ${jobId}`} <code>{jobId}</code></span><button type="button" className="text-button compact" onClick={()=>resetJobSkills(jobId)}>Reset</button></h4>{jobSkills.map(skill=><div className="skill-edit-row" key={skill.skillid}><span><strong>{skill.skill_name||`Skill ${skill.skillid}`}</strong><code>{skill.skillid} | max {skill.max_level||0}</code></span><input type="number" min="0" max={skill.max_level||0} value={skill.skilllevel||0} onChange={e=>setSkills(rows=>rows.map(row=>row.skillid===skill.skillid?{...row,skilllevel:Number(e.target.value)}:row))} onBlur={e=>setSkillLevel(skill,Number(e.target.value),false)}/><div className="skill-stepper"><button type="button" className="icon-link" onClick={()=>setSkillLevel(skill,Number(skill.skilllevel||0)-1,true)}>-</button><button type="button" className="icon-link" onClick={()=>setSkillLevel(skill,Number(skill.skilllevel||0)+1,true)}>+</button></div></div>)}</div>})}</article>)}</div>}
      </section>
    </div>}</>
}

function Inventory({notify,focusCharacter,onCharacterSelect,onOpen,jump,onInspectorChange}:{notify:Notify;focusCharacter?:number;onCharacterSelect?:(id:number)=>void;onOpen:(type:string,id:number)=>void;jump:(x:JumpTarget)=>void;onInspectorChange:(size:"none"|"standard"|"wide")=>void}){
  const [character,setCharacter]=useState<any|null>(null);const [items,setItems]=useState<any[]>([]);const [editing,setEditing]=useState<any|null>(null);const [editingStorage,setEditingStorage]=useState<any|null>(null);const [storage,setStorage]=useState<any[]>([]);
  const [accounts,setAccounts]=useState<any[]>([]);const [browseAccount,setBrowseAccount]=useState<any>();const [accountCharacters,setAccountCharacters]=useState<any[]>([]);
  const [accountQuery,setAccountQuery]=useState("");const [worldFilter,setWorldFilter]=useState("all");
  useEffect(()=>{
    onInspectorChange(editing||editingStorage?"wide":"none");
    return()=>onInspectorChange("none");
  },[editing,editingStorage,onInspectorChange]);
  useEffect(()=>{api<Page<any>>("/api/accounts?sort=name&direction=asc&page=0&size=200").then(r=>setAccounts(r.items))},[]);
  async function chooseAccount(account:any){setBrowseAccount(account);setAccountCharacters(await api<any[]>(`/api/accounts/${account.id}/characters`))}
  async function selectCharacter(row:any){const id=Number(row.id);const details=await api<any>(`/api/characters/${id}`);setCharacter(details);onCharacterSelect?.(id)}
  useEffect(()=>{if(focusCharacter&&Number(character?.id)!==Number(focusCharacter))api<any>(`/api/characters/${focusCharacter}`).then(row=>{setCharacter(row);onCharacterSelect?.(Number(row.id))})},[focusCharacter]);
  useEffect(()=>{if(!character?.accountid||!accounts.length)return;const account=accounts.find(row=>Number(row.id)===Number(character.accountid));if(account&&Number(browseAccount?.id)!==Number(account.id)){setBrowseAccount(account);api<any[]>(`/api/accounts/${account.id}/characters`).then(setAccountCharacters)}},[character?.accountid,accounts.length]);
  async function load(){if(!character)return;setItems(await api<any[]>(`/api/characters/${character.id}/inventory`));setStorage(await api<any[]>(`/api/accounts/${character.accountid}/storage?world=${character.world}`))}
  useEffect(()=>{if(character)load()},[character]);
  async function move(item:any,position:number){await api(`/api/characters/${character.id}/inventory/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify({...item,itemId:item.itemid,position,quantity:item.quantity||1,equipment:item.inventorytype===1?equipFrom(item):null,reason:"Moved in Console"})});load()}
  async function moveOrSwap(item:any,target:any,position:number){if(target)await api(`/api/characters/${character.id}/inventory/swap`,{method:"POST",body:JSON.stringify({firstItemId:item.inventoryitemid,secondItemId:target.inventoryitemid,reason:"Swapped in Console"})});else await move(item,position);load()}
  async function duplicate(item:any){await api(`/api/characters/${character.id}/inventory/${item.inventoryitemid}/duplicate`,{method:"POST",body:JSON.stringify({reason:"Duplicated in Console"})});notify("Item duplicated into next empty slot");load()}
  async function moveStorage(item:any,position:number){await api(`/api/accounts/${character.accountid}/storage/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify({world:character.world,position,quantity:item.quantity||1,equipment:item.inventorytype===1?equipFrom(item):null,reason:"Moved in Console"})});load()}
  async function moveOrSwapStorage(item:any,target:any,position:number){if(target)await api(`/api/accounts/${character.accountid}/storage/swap`,{method:"POST",body:JSON.stringify({world:character.world,firstItemId:item.inventoryitemid,secondItemId:target.inventoryitemid,reason:"Swapped in Console"})});else await moveStorage(item,position);load()}
  async function saveStorageMeso(value:number){await api(`/api/accounts/${character.accountid}/storage`,{method:"PATCH",body:JSON.stringify({world:character.world,slots:Number(storage[0]?.slots||48),meso:value,reason:"Updated storage mesos through Console"})});notify("Storage mesos updated");load()}
  async function expandStorage(){await api(`/api/accounts/${character.accountid}/storage`,{method:"PATCH",body:JSON.stringify({world:character.world,slots:48,meso:Number(storage[0]?.meso||0),reason:"Expanded account storage to 48 slots"})});notify("Storage expanded to 48 slots");load()}
  function firstInventorySlot(type:number){const limit=limits[type]||96;for(let slot=1;slot<=limit;slot++)if(!items.some(x=>x.inventorytype===type&&x.position===slot))return slot;return null}
  function firstStorageSlot(){const limit=Number(storage[0]?.slots||48);for(let slot=1;slot<=limit;slot++)if(!storage.some(x=>x.inventoryitemid&&x.position===slot))return slot;return null}
  async function transferInventoryToStorage(item:any){const position=firstStorageSlot();if(position==null){notify("Storage is full");return}
    const stored=await api<any>(`/api/accounts/${character.accountid}/storage`,{method:"POST",body:JSON.stringify({world:character.world,itemId:item.itemid,position,quantity:item.quantity||1,equipment:item.inventorytype===1?equipFrom(item):null,reason:"Moved from inventory to storage"})});
    await api(`/api/characters/${character.id}/inventory/${item.inventoryitemid}?reason=Moved%20to%20storage`,{method:"DELETE"});notify("Item moved to storage");setEditing(null);setEditingStorage(stored);setItems(rows=>rows.filter(row=>Number(row.inventoryitemid)!==Number(item.inventoryitemid)));setStorage(rows=>[...rows.filter(row=>Number(row.inventoryitemid)!==Number(stored.inventoryitemid)),stored])}
  async function transferStorageToInventory(item:any){const type=item.inventorytype||Math.floor(item.itemid/1_000_000);const position=firstInventorySlot(type);if(position==null){notify("Inventory category is full");return}
    const added=await api<any>(`/api/characters/${character.id}/inventory`,{method:"POST",body:JSON.stringify({itemId:item.itemid,position,quantity:item.quantity||1,owner:item.owner||"",flag:item.flag||0,expiration:item.expiration??-1,giftFrom:item.giftFrom||"",equipment:type===1?equipFrom(item):null,reason:"Moved from storage to inventory"})});
    await api(`/api/accounts/${character.accountid}/storage/${item.inventoryitemid}?world=${character.world}&reason=Moved%20to%20inventory`,{method:"DELETE"});notify("Item moved to inventory");setEditingStorage(null);setEditing(added);setStorage(rows=>rows.filter(row=>Number(row.inventoryitemid)!==Number(item.inventoryitemid)));setItems(rows=>[...rows.filter(row=>Number(row.inventoryitemid)!==Number(added.inventoryitemid)),added])}
  const limits=[0,character?.equipslots||96,character?.useslots||96,character?.setupslots||96,character?.etcslots||96,96];
  const groups=useMemo(()=>[1,2,3,4,5].map(type=>({type,items:items.filter(x=>x.inventorytype===type)})),[items]);
  const accountWorlds=useMemo(()=>Array.from(new Set(accounts.flatMap(row=>String(row.worlds||"").split(",").filter(Boolean)))).sort((a,b)=>Number(a)-Number(b)),[accounts]);
  const visibleAccounts=accounts.filter(row=>{
    const rowWorlds=String(row.worlds||"").split(",").filter(Boolean);
    const haystack=`${row.name||""} ${row.id||""} ${row.character_names||""}`.toLowerCase();
    return (!accountQuery||haystack.includes(accountQuery.toLowerCase()))
      && (worldFilter==="all"||rowWorlds.includes(worldFilter));
  });
  const visibleCharacters=accountCharacters.filter(c=>{
    return worldFilter==="all"||String(c.world)===worldFilter;
  });
  return <><div className="inventory-account-toolbar"><SearchInput value={accountQuery} setValue={setAccountQuery} placeholder="Search account, character or ID"/>
    <select value={worldFilter} onChange={e=>setWorldFilter(e.target.value)}><option value="all">All worlds</option>{accountWorlds.map(world=><option value={world} key={world}>World {world}</option>)}</select></div>
    <div className="inventory-browser"><div><h3>Browse accounts</h3>{visibleAccounts.map(a=><button className={browseAccount?.id===a.id?"selected":""} key={a.id} onClick={()=>chooseAccount(a)}><UsersRound/><span><strong>{a.name}</strong><small>World {a.worlds||"-"} | {a.banned?"Banned":a.loggedin?"Online":a.mute?"Muted":"Active"} | GM {a.max_gm||0}</small><small>{a.character_count}/{a.characterslots||3} characters</small></span></button>)}</div>
    <div><h3>{browseAccount?`${browseAccount.name}'s characters`:"Choose an account"}</h3>{visibleCharacters.map(c=><button key={c.id} className={Number(character?.id)===Number(c.id)?"selected":""} onClick={()=>selectCharacter(c)}><CircleUserRound/><span><strong>{browseAccount.name} → {c.name}</strong><small>World {c.world} | Lv. {c.level} {c.job_name||"Unknown job"} ({c.job}) | GM {c.gm||0}</small></span></button>)}</div></div>
    {character&&<div className="character-strip"><CircleUserRound/><div><strong>{character.account_name} → {character.name}</strong><span>Lv. {character.level} | {character.job_name||"Unknown job"} ({character.job}) | {character.map_name||"Unknown map"} ({character.map})</span></div><button className="secondary" onClick={load}><RefreshCw size={14}/>Refresh</button></div>}
    {!character?<Empty text="Choose an account and character to edit inventory and storage"/>:<>{groups.map(group=><article className="panel inventory-panel" key={group.type}><div className="inventory-panel-head"><PanelTitle title={["","Equip","Use","Setup","Etc","Cash"][group.type]} subtitle="Click an item to edit. Drag onto an empty slot to move."/>
      <div className="inventory-transfer-zone" onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();const raw=e.dataTransfer.getData("item");if(raw)transferInventoryToStorage(JSON.parse(raw))}}>Drag here to put in storage</div></div>
      <div className="slot-grid expanded">{Array.from({length:limits[group.type]},(_,index)=>index+1).map(slot=>{const item=group.items.find(x=>x.position===slot);return <button key={slot} className={`item-slot ${item?"occupied":""}`}
        draggable={!!item} onDragStart={e=>item&&e.dataTransfer.setData("item",JSON.stringify(item))} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();const dragged=JSON.parse(e.dataTransfer.getData("item"));if(dragged.inventoryitemid!==item?.inventoryitemid)moveOrSwap(dragged,item,slot)}}
        onClick={()=>item?setEditing(item):setEditing({newItem:true,position:slot,inventorytype:group.type})} title={item?itemTooltip(item):`Empty ${slot}`}>
        <span>{slot}</span>{item&&<><img src={assetUrl("ITEM",item.itemid)} alt=""/>{group.type!==1&&<b>{item.quantity||1}</b>}</>}</button>})}</div></article>)}
      <article className="panel inventory-panel"><div className="storage-heading"><PanelTitle title="Account storage" subtitle={`World ${character.world}`}/>
        <div className="storage-tools">{Number(storage[0]?.slots||0)<48&&<button className="secondary" onClick={expandStorage}>Expand storage to 48 slots</button>}
          <StorageMesoEditor value={Number(storage[0]?.meso||0)} save={saveStorageMeso}/><div className="inventory-transfer-zone" onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();const raw=e.dataTransfer.getData("storageItem");if(raw)transferStorageToInventory(JSON.parse(raw))}}>Drag here to put in inventory</div></div></div>
        <div className="slot-grid expanded">{Array.from({length:storage[0]?.slots||48},(_,i)=>i+1).map(slot=>{const item=storage.find(x=>x.inventoryitemid&&x.position===slot);return <button className={`item-slot ${item?"occupied":""}`} key={slot}
          draggable={!!item} onDragStart={e=>item&&e.dataTransfer.setData("storageItem",JSON.stringify(item))} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();const dragged=JSON.parse(e.dataTransfer.getData("storageItem"));if(dragged.inventoryitemid!==item?.inventoryitemid)moveOrSwapStorage(dragged,item,slot)}}
          onClick={()=>setEditingStorage(item||{newItem:true,position:slot})} title={item?itemTooltip(item):`Empty storage slot ${slot}`}><span>{slot}</span>{item&&<><img src={assetUrl("ITEM",item.itemid)} alt=""/>{Number(item.inventorytype)!==1&&<b>{item.quantity||1}</b>}</>}</button>})}</div></article></>}
    {editing&&character&&<ItemEditor key={`${editing.newItem?"new":editing.inventoryitemid}-${editing.position}`} item={editing} characterId={character.id} close={()=>setEditing(null)} saved={savedItem=>{if(!savedItem){setItems(rows=>{const remaining=rows.filter(row=>Number(row.inventoryitemid)!==Number(editing.inventoryitemid));setEditing(nearestInventoryItem(remaining,editing));return remaining});return}setItems(rows=>{const exists=rows.some(row=>Number(row.inventoryitemid)===Number(savedItem.inventoryitemid));return exists?rows.map(row=>Number(row.inventoryitemid)===Number(savedItem.inventoryitemid)?savedItem:row):[...rows,savedItem]});setEditing(savedItem)}} notify={notify} duplicate={duplicate} onOpen={onOpen} jump={jump}/>}
    {editingStorage&&character&&<ItemEditor key={`storage-${editingStorage.newItem?"new":editingStorage.inventoryitemid}-${editingStorage.position}`} item={editingStorage} characterId={character.id} accountId={character.accountid} world={character.world} close={()=>setEditingStorage(null)} saved={savedItem=>{if(!savedItem){setStorage(rows=>{const remaining=rows.filter(row=>Number(row.inventoryitemid)!==Number(editingStorage.inventoryitemid));setEditingStorage(nearestStorageItem(remaining,editingStorage));return remaining});return}setStorage(rows=>{const exists=rows.some(row=>Number(row.inventoryitemid)===Number(savedItem.inventoryitemid));return exists?rows.map(row=>Number(row.inventoryitemid)===Number(savedItem.inventoryitemid)?savedItem:row):[...rows,savedItem]});setEditingStorage(savedItem)}} notify={notify} duplicate={async()=>{}} allowDuplicate={false} onOpen={onOpen} jump={jump} storageMode/>}</>
}

function CharacterDockCard({character,onNavigate}:{character:any;onNavigate:(view:View,id:number)=>void}){
  const id=Number(character.id);
  return <article className="character-dock-card">
    <button type="button" className="character-dock-main" onClick={()=>onNavigate("character-stats",id)}>
      <div className="avatar-placeholder"><CircleUserRound/></div>
      <span><strong>{character.name}</strong><small>Lv. {character.level} | {character.job_name||"Unknown job"} ({character.job})</small><small>{Number(character.meso||0).toLocaleString()} mesos | {character.map_name||"Unknown map"} ({character.map})</small></span>
      <ChevronRight size={15}/>
    </button>
    <div className="character-dock-actions" aria-label={`${character.name} pages`}>
      <button type="button" onClick={()=>onNavigate("inventory",id)}>Inventory / Storage</button>
      <button type="button" onClick={()=>onNavigate("character-equipment",id)}>Equipment / Appearance</button>
    </div>
  </article>
}

function nearestInventoryItem(rows:any[],deleted:any){
  const deletedType=Number(deleted?.inventorytype);
  const deletedPosition=Number(deleted?.position);
  const sameType=rows
    .filter(row=>Number(row.inventorytype)===deletedType)
    .sort((a,b)=>Number(a.position)-Number(b.position));
  return [...sameType].reverse().find(row=>Number(row.position)<deletedPosition)
    || sameType.find(row=>Number(row.position)>deletedPosition)
    || null;
}

function nearestStorageItem(rows:any[],deleted:any){
  const deletedPosition=Number(deleted?.position);
  const occupied=rows
    .filter(row=>row.inventoryitemid)
    .sort((a,b)=>Number(a.position)-Number(b.position));
  return [...occupied].reverse().find(row=>Number(row.position)<deletedPosition)
    || occupied.find(row=>Number(row.position)>deletedPosition)
    || null;
}

function EquipmentAppearance({notify,focusCharacter,onCharacterSelect,onOpen,jump,onInspectorChange}:{notify:Notify;focusCharacter?:number;onCharacterSelect?:(id:number)=>void;onOpen:(type:string,id:number)=>void;jump:(x:JumpTarget)=>void;onInspectorChange:(size:"none"|"standard"|"wide")=>void}){
  const [character,setCharacter]=useState<any|null>(null);const [items,setItems]=useState<any[]>([]);const [editing,setEditing]=useState<any|null>(null);
  const [accounts,setAccounts]=useState<any[]>([]);const [browseAccount,setBrowseAccount]=useState<any>();const [accountCharacters,setAccountCharacters]=useState<any[]>([]);
  const [accountQuery,setAccountQuery]=useState("");const [worldFilter,setWorldFilter]=useState("all");const [appearance,setAppearance]=useState<any>({});const [showCash,setShowCash]=useState(true);
  useEffect(()=>{onInspectorChange(editing?"wide":"none");return()=>onInspectorChange("none")},[editing,onInspectorChange]);
  useEffect(()=>{api<Page<any>>("/api/accounts?sort=name&direction=asc&page=0&size=200").then(r=>setAccounts(r.items))},[]);
  async function chooseAccount(account:any){setBrowseAccount(account);setAccountCharacters(await api<any[]>(`/api/accounts/${account.id}/characters`))}
  async function selectCharacter(row:any){const id=Number(row.id);const details=await api<any>(`/api/characters/${id}`);setCharacter(details);setAppearance({hair:details.hair??0,face:details.face??0,skincolor:details.skincolor??0,gender:details.gender??0});onCharacterSelect?.(id)}
  useEffect(()=>{if(focusCharacter&&Number(character?.id)!==Number(focusCharacter))selectCharacter({id:focusCharacter})},[focusCharacter]);
  useEffect(()=>{if(!character?.accountid||!accounts.length)return;const account=accounts.find(row=>Number(row.id)===Number(character.accountid));if(account&&Number(browseAccount?.id)!==Number(account.id)){setBrowseAccount(account);api<any[]>(`/api/accounts/${account.id}/characters`).then(setAccountCharacters)}},[character?.accountid,accounts.length]);
  async function load(){if(!character)return[];const rows=(await api<any[]>(`/api/characters/${character.id}/inventory`)).filter(item=>Number(item.position)<0);setItems(rows);return rows}
  useEffect(()=>{if(character)load()},[character]);
  async function saveAppearance(next:any){if(!character)return;setAppearance(next);const updated=await api<any>(`/api/characters/${character.id}/appearance`,{method:"PATCH",body:JSON.stringify({...next,reason:"Edited appearance through Equipment / Appearance page"})});setCharacter(updated);notify("Appearance updated")}
  const accountWorlds=useMemo(()=>Array.from(new Set(accounts.flatMap(row=>String(row.worlds||"").split(",").filter(Boolean)))).sort((a,b)=>Number(a)-Number(b)),[accounts]);
  const visibleAccounts=accounts.filter(row=>{const rowWorlds=String(row.worlds||"").split(",").filter(Boolean);const haystack=`${row.name||""} ${row.id||""} ${row.character_names||""}`.toLowerCase();return (!accountQuery||haystack.includes(accountQuery.toLowerCase()))&&(worldFilter==="all"||rowWorlds.includes(worldFilter))});
  const visibleCharacters=accountCharacters.filter(c=>worldFilter==="all"||String(c.world)===worldFilter);
  const equipped=items.filter(item=>Number(item.position)<0&&Number(item.position)>-100).sort((a,b)=>Number(b.position)-Number(a.position));
  const cashEquipped=items.filter(item=>Number(item.position)<=-100).sort((a,b)=>Number(b.position)-Number(a.position));
  const nonCashAvatarEquips=avatarEquipIdsWithUnderwear(equipped,Number(appearance.gender||0));
  const cashAvatarEquips=avatarEquipIdsWithUnderwear([...cashEquipped,...equipped],Number(appearance.gender||0));
  const nonCashAvatarSource=avatarUrl({skinColor:Number(appearance.skincolor||0),hair:Number(appearance.hair||0),face:Number(appearance.face||0),equips:nonCashAvatarEquips});
  const cashAvatarSource=cashEquipped.length?avatarUrl({skinColor:Number(appearance.skincolor||0),hair:Number(appearance.hair||0),face:Number(appearance.face||0),equips:cashAvatarEquips}):nonCashAvatarSource;
  const avatarSource=showCash?cashAvatarSource:nonCashAvatarSource;
  const renderEquipped=(rows:any[],title:string,slots:readonly (readonly [number,string])[])=><article className="panel inventory-panel"><PanelTitle title={title} subtitle="Click equipment to edit stats and view catalog details."/><div className="slot-grid equipment-slot-grid">{slots.map(([position,label])=>{
    const item=rows.find(row=>Number(row.position)===position);
    return <button key={`${title}-${position}`} className={`item-slot equipment-slot ${item?"occupied":""}`} onClick={()=>setEditing(item||{newItem:true,position,inventorytype:1})} title={item?itemTooltip(item):`Empty ${label} slot (${position})`}>
      <span>{label}</span>{item?<img src={assetUrl("ITEM",item.itemid)} alt={item.item_name||String(item.itemid)}/>:<small>Empty</small>}
    </button>})}</div></article>;
  return <><div className="inventory-account-toolbar"><SearchInput value={accountQuery} setValue={setAccountQuery} placeholder="Search account, character or ID"/><select value={worldFilter} onChange={e=>setWorldFilter(e.target.value)}><option value="all">All worlds</option>{accountWorlds.map(world=><option value={world} key={world}>World {world}</option>)}</select></div>
    <div className="inventory-browser"><div><h3>Browse accounts</h3>{visibleAccounts.map(a=><button className={browseAccount?.id===a.id?"selected":""} key={a.id} onClick={()=>chooseAccount(a)}><UsersRound/><span><strong>{a.name}</strong><small>World {a.worlds||"-"} | {a.banned?"Banned":a.loggedin?"Online":a.mute?"Muted":"Active"} | GM {a.max_gm||0}</small><small>{a.character_count}/{a.characterslots||3} characters</small></span></button>)}</div>
      <div><h3>{browseAccount?`${browseAccount.name}'s characters`:"Choose an account"}</h3>{visibleCharacters.map(c=><button key={c.id} className={Number(character?.id)===Number(c.id)?"selected":""} onClick={()=>selectCharacter(c)}><CircleUserRound/><span><strong>{browseAccount.name} {"->"} {c.name}</strong><small>World {c.world} | Lv. {c.level} {c.job_name||"Unknown job"} ({c.job}) | GM {c.gm||0}</small></span></button>)}</div></div>
    {!character?<Empty text="Choose an account and character to edit equipment and appearance"/>:<><div className="equipment-appearance-shell">
      <article className="panel avatar-preview-panel"><div className="panel-title avatar-panel-title"><div><h2>{character.account_name} {"->"} {character.name}</h2><p>{character.job_name||"Unknown job"} ({character.job}) | Lv. {character.level}</p></div><label className="filter-check"><input type="checkbox" checked={showCash} onChange={event=>setShowCash(event.target.checked)}/>Cash</label></div><div className="avatar-preview-body"><div className="avatar-stage"><img className="avatar-fullbody" src={avatarSource} alt={`${character.name} avatar`}/><strong>{character.name}</strong><small>{showCash&&cashEquipped.length?"Cash view":"Equip view"}</small></div></div></article>
      <article className="panel appearance-controls"><PanelTitle title="Appearance" subtitle="Search by id or name, then select to save."/><div className="appearance-stack"><AppearanceCatalogSelect label="Hair" type="HAIR" value={Number(appearance.hair||0)} onSelect={entity=>saveAppearance({...appearance,hair:entity.entity_id})}/><AppearanceCatalogSelect label="Face" type="FACE" value={Number(appearance.face||0)} onSelect={entity=>saveAppearance({...appearance,face:entity.entity_id})}/><ChoiceSelect label="Skin" value={Number(appearance.skincolor||0)} choices={skinChoices} onSelect={choice=>saveAppearance({...appearance,skincolor:choice.id})}/><GenderIconSelect value={Number(appearance.gender||0)} onSelect={value=>saveAppearance({...appearance,gender:value})}/></div></article>
    </div>{renderEquipped(equipped,"Equipped items",equipmentSlots)}{renderEquipped(cashEquipped,"Cash equipped items",cashEquipmentSlots)}</>}
    {editing&&character&&<ItemEditor key={`${editing.newItem?"new":editing.inventoryitemid}-${editing.position}`} item={editing} characterId={character.id} close={()=>setEditing(null)} saved={savedItem=>{if(!savedItem){setEditing(null);setItems(rows=>rows.filter(row=>Number(row.inventoryitemid)!==Number(editing.inventoryitemid)));return}setItems(rows=>{const exists=rows.some(row=>Number(row.inventoryitemid)===Number(savedItem.inventoryitemid));return exists?rows.map(row=>Number(row.inventoryitemid)===Number(savedItem.inventoryitemid)?savedItem:row):[...rows,savedItem]});setEditing(savedItem)}} notify={notify} duplicate={async()=>{}} allowDuplicate={false} onOpen={onOpen} jump={jump}/>}</>
}

function CharacterEditor({characterId,close,saved}:{characterId:number;close:()=>void;saved:(x:any)=>void}){
  const [data,setData]=useState<any>();useEffect(()=>{api(`/api/characters/${characterId}`).then(setData)},[characterId]);
  async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();const f=new FormData(e.currentTarget);const value=(name:string)=>Number(f.get(name)||0);
    const updated=await api<any>(`/api/characters/${characterId}`,{method:"PATCH",body:JSON.stringify({level:value("level"),job:value("job"),gm:value("gm"),str:value("str"),dex:value("dex"),intStat:value("int"),luk:value("luk"),hp:value("hp"),mp:value("mp"),maxHp:value("maxhp"),maxMp:value("maxmp"),ap:value("ap"),sp:data.sp||"0,0,0,0,0,0,0,0,0,0",meso:value("meso"),fame:value("fame"),map:value("map"),reason:String(f.get("reason"))})});saved(updated)}
  if(!data)return <div className="drawer inspector"><Loading/></div>;
  const fields=[["level","Level"],["job","Job ID"],["gm","GM level"],["str","STR"],["dex","DEX"],["int","INT"],["luk","LUK"],["hp","HP"],["mp","MP"],["maxhp","Max HP"],["maxmp","Max MP"],["ap","Available AP"],["meso","Mesos"],["fame","Fame"],["map","Map ID"]];
  return <form className="drawer inspector editor-dock" onSubmit={submit}><button type="button" className="modal-close" onClick={close}><X/></button>
    <PanelTitle title={`Edit ${data.account_name} → ${data.name}`} subtitle={`${data.job_name||"Unknown job"} (${data.job}) | ${data.map_name||"Unknown map"} (${data.map}) | account must be offline`}/>
    <div className="field-grid">{fields.map(([key,label])=><label key={key}>{label}<input name={key} type="number" defaultValue={data[key]||0}/></label>)}</div>
    <label>Audit reason<textarea name="reason" required defaultValue="Edited through character manager"/></label><div className="modal-actions"><button className="primary">Save character</button></div>
  </form>
}

function ItemEditor({item,characterId,accountId,world,close,saved,notify,duplicate,onOpen,jump,allowDuplicate=true,storageMode=false}:{item:any;characterId:number;accountId?:number;world?:number;close:()=>void;saved:(item?:any)=>void|Promise<void>;notify:Notify;duplicate:(x:any)=>void;onOpen:(t:string,id:number)=>void;jump:(x:JumpTarget)=>void;allowDuplicate?:boolean;storageMode?:boolean}){
  const [selected,setSelected]=useState<Entity|null>(null);
  const [confirmDelete,setConfirmDelete]=useState(false);
  const [original,setOriginal]=useState<any>();useEffect(()=>{const id=selected?.entity_id||item.itemid;if(id)api(`/api/catalog/ITEM/${id}`).then(setOriginal)},[selected,item.itemid]);
  const itemCategory=Math.floor(Number((selected?.entity_id||item.itemid)||0)/1000000);
  const isEquipment=Number(item.inventorytype)===1||Number(item.position)<0||itemCategory===1;
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
    const equipment=isEquipment?Object.fromEntries(equipmentFields.map(([name])=>[name,Number(f.get(name)??0)])):null;
    const itemId=selected?.entity_id||item.itemid;
    const payload={itemId,position:Number(f.get("position")),quantity:isEquipment?1:Number(f.get("quantity")),
      owner:String(f.get("owner")??""),flag:Number(f.get("flag")??0),expiration:Number(f.get("expiration")??-1),
      giftFrom:String(f.get("giftFrom")??""),equipment,reason:String(f.get("reason"))};
    const storagePayload={world, itemId, position:payload.position, quantity:payload.quantity, equipment, reason:payload.reason};
    const savedItem=storageMode
      ? item.newItem
        ? (selected&&accountId!=null?await api<any>(`/api/accounts/${accountId}/storage`,{method:"POST",body:JSON.stringify(storagePayload)}):null)
        : accountId!=null?await api<any>(`/api/accounts/${accountId}/storage/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify(storagePayload)}):null
      : item.newItem
        ? (selected?await api<any>(`/api/characters/${characterId}/inventory`,{method:"POST",body:JSON.stringify(payload)}):null)
        : await api<any>(`/api/characters/${characterId}/inventory/${item.inventoryitemid}`,{method:"PATCH",body:JSON.stringify(payload)});
    if(!savedItem)return;
    notify(item.newItem?(storageMode?"Storage item added":"Item added"):(storageMode?"Storage item updated":"Item updated"));await saved(savedItem)}
  async function remove(){if(storageMode&&accountId!=null&&world!=null)await api(`/api/accounts/${accountId}/storage/${item.inventoryitemid}?world=${world}&reason=Deleted%20from%20Console`,{method:"DELETE"});else await api(`/api/characters/${characterId}/inventory/${item.inventoryitemid}?reason=Deleted%20from%20Console`,{method:"DELETE"});notify(storageMode?"Storage item deleted":"Item deleted");setConfirmDelete(false);await saved()}
  const originalProps=metadata(original?.properties_json);
  const editSection=<section className="inventory-edit-section"><h3>{item.newItem?"New item values":"Current saved item values"}</h3>
    <Autocomplete type="ITEM" subtype={storageMode?"":inventoryItemTypes[Math.max(0,itemCategory-1)]} value={selected} onSelect={setSelected} placeholder={item.newItem?storageMode?"Search any item":"Search compatible item":"Search to replace with another compatible item"}/>
    {!item.newItem&&<div className="record-identifiers"><span>Inventory record <code>{item.inventoryitemid}</code></span><span>Inventory type <code>{item.inventorytype}</code></span>
      <span>Pet link <code>{item.petid??-1}</code></span>{item.inventorytype===1&&<span>Ring link <code>{item.ringid??-1}</code></span>}</div>}
    <p className="edit-hint">Click any value below, type the replacement value, then save. Relationship IDs are shown above but remain read-only to protect linked pet and ring records.</p>
    <div className="field-grid editable-grid">
      <label>Position<input name="position" type="number" min={storageMode?1:isEquipment?-200:1} defaultValue={item.position??1}/></label>
      <label>Quantity<input name="quantity" type="number" min="1" disabled={isEquipment} defaultValue={isEquipment?1:item.quantity??1}/></label>
      {!storageMode&&<><label>Owner<input name="owner" defaultValue={item.owner??""}/></label>
      <label>Item flag<input name="flag" type="number" min="0" defaultValue={item.flag??0}/></label>
      <label>Expiration timestamp<input name="expiration" type="number" defaultValue={item.expiration??-1}/></label>
      <label>Gift from<input name="giftFrom" maxLength={26} defaultValue={item.giftFrom??""}/></label></>}
    </div>
    {isEquipment&&<><h3>Current equipment stats</h3><p className="edit-hint">These are the exact values stored on this equipment, not its WZ average.</p>
      <div className="equipment-stat-groups editable-grid">
        {renderEquipmentGroup(["str","dex","intStat","luk"],"four")}
        {renderEquipmentGroup(["watk","matk"],"two")}
        {renderEquipmentGroup(["hp","mp","wdef","mdef"],"four")}
        {renderEquipmentGroup(["speed","jump","hands"],"three")}
        {renderEquipmentGroup(["acc","avoid"],"two")}
        {renderEquipmentGroup(["level","upgradeSlots"],"two")}
        {renderEquipmentGroup(["locked","vicious","itemLevel","itemExp"],"four")}
      </div></>}
    <label>Audit reason<textarea name="reason" required defaultValue={item.newItem?(storageMode?"Added through storage editor":"Granted through inventory editor"):(storageMode?"Edited through storage editor":"Edited through inventory editor")}/></label>
    <div className="modal-actions inventory-editor-actions">{!item.newItem&&<><button type="button" className="danger-button" onClick={()=>setConfirmDelete(true)}><Trash2 size={15}/>Delete</button>{allowDuplicate&&<button type="button" className="secondary" onClick={()=>duplicate(item)}><PackagePlus size={15}/>Duplicate</button>}</>}<button className="primary">Save</button></div>
  </section>;
  return <><form className="drawer inspector editor-dock inventory-editor-dock" onSubmit={submit}><button type="button" className="modal-close" onClick={close}><X/></button>
    <div className="inventory-values-column">{editSection}</div>
    <div className="inventory-info-column">
      {original?<div className="drawer-hero inventory-item-hero"><img src={assetUrl("ITEM",original.entity_id)} alt=""/><div><div className="tag-row"><span className="tag">ITEM</span><span className="tag soft">{original.category||original.subtype}</span></div><h2>{original.name}</h2><code>{original.entity_id}</code><p>{original.description||"No String.wz description"}</p></div></div>
        :<PanelTitle title={item.newItem?`Add item to slot ${item.position}`:item.item_name||`Item ${item.itemid}`} subtitle="Loading catalog information"/>}
      {originalProps.statRanges&&<section><h3>Average stats and server roll range</h3><StatTable ranges={originalProps.statRanges}/></section>}
      {original&&<><LinkedRows type="MOB" title="Dropped by" rows={original.droppedBy} labelKey="name" idKey="id" collapsible click={r=>{close();jump({view:"mobs",type:"MOB",id:r.id})}}/>
        <LinkedRows type="NPC" title="Sold by" rows={original.soldBy} labelKey="name" idKey="id" collapsible click={r=>{close();jump({view:"shops",id:r.shopid})}}/>
        <LinkedRows type="GACHA" title="Available from gachapon" rows={original.gachapon} labelKey="location_code" idKey="npc_id" collapsible click={r=>{close();jump({view:"gacha",location:r.location_code})}}/>
        <LinkedRows type="CHARACTER" title="Owned by characters" rows={original.ownedBy} labelKey="name" idKey="id" collapsible defaultOpen={false} click={r=>{close();jump({view:"inventory",id:r.id})}} characterJump={(targetView,id)=>{close();jump({view:targetView,id})}}/></>}
      {original&&<section className="technical"><h3>Technical provenance</h3><PropertyGrid data={originalProps}/><code className="source-code">WZ: {original.source_path}</code>
        <code className="source-code">WZ image node: {wzImageNode("ITEM",original.entity_id,original.source_path,metadata(original.properties_json))}</code>
        {assetUrls("ITEM",original.entity_id).map((source,index)=><code className="source-code" key={source}>Image {index===0?"source":"fallback"}: {source}</code>)}</section>}
    </div>
  </form>
  {confirmDelete&&<ConfirmDialog title={storageMode?"Delete storage item permanently?":"Delete item permanently?"} message={`${item.item_name||`Item ${item.itemid}`} will be removed from ${storageMode?"account storage":"this character's inventory"}. This cannot be undone.`} confirmLabel="Delete item" onCancel={()=>setConfirmDelete(false)} onConfirm={remove}/>}
  </>
}

function EntityDrawer({entity,close,jump,history,historyIndex,moveHistory,named}:{entity:{type:string;id:number;context?:string};close:()=>void;jump:(x:JumpTarget)=>void;history:HistoryEntry[];historyIndex:number;moveHistory:(index:number)=>void;named:(type:string,id:number,name:string)=>void}){
  const [data,setData]=useState<any>();useEffect(()=>{setData(undefined);api<any>(`/api/catalog/${entity.type}/${entity.id}`).then(value=>{setData(value);named(entity.type,entity.id,value.name)})},[entity]);
  if(!data)return <div className="drawer"><button className="modal-close" onClick={close}><X/></button><Loading/></div>;
  const props=metadata(data.properties_json);
  const imageSources=assetUrls(entity.type,entity.id,props);
  return <div className="drawer"><button className="modal-close" onClick={close}><X/></button><div className="drawer-history"><button disabled={historyIndex<=0} onClick={()=>moveHistory(historyIndex-1)}><ChevronLeft/><span>{history[historyIndex-1]?.name||"Back"}</span></button><select value={historyIndex} onChange={e=>moveHistory(Number(e.target.value))}>{history.map((entry,index)=><option value={index} key={`${entry.type}-${entry.id}-${index}`}>{entry.name||`${entry.type} ${entry.id}`}</option>)}</select><button disabled={historyIndex>=history.length-1} onClick={()=>moveHistory(historyIndex+1)}><span>{history[historyIndex+1]?.name||"Forward"}</span><ChevronRight/></button></div>
    <div className="drawer-hero"><EntityImage type={entity.type} id={entity.id} properties={props}/><div>
    <div className="tag-row"><span className="tag">{entity.type}</span>{data.subtype&&<span className="tag soft">{data.subtype}</span>}{data.category&&<span className="tag soft">{data.category}</span>}{data.used_in_game&&<span className="tag used">Used in server data</span>}</div>
    <h2>{data.name}</h2><code>{entity.id}</code><p>{data.description||"No String.wz description"}</p></div></div>
    {props.statRanges&&<section><h3>Average stats and server roll range</h3><StatTable ranges={props.statRanges}/></section>}
    {entity.type==="MOB"&&<><LinkedRows type="ITEM" title="Drops" rows={(data.drops||[]).filter((r:any)=>r.itemid>0)} labelKey="item_name" idKey="itemid" click={r=>jump({view:"items",type:"ITEM",id:r.itemid})}/>
      <LinkedRows type="MAP" title="Spawn maps" rows={data.spawns} labelKey="map_name" idKey="map_id" click={r=>jump({view:"maps",type:"MAP",id:r.map_id})}/></>}
    {entity.type==="ITEM"&&<><LinkedRows type="MOB" title="Dropped by" rows={data.droppedBy} labelKey="name" idKey="id" collapsible click={r=>jump({view:"mobs",type:"MOB",id:r.id})}/>
      <LinkedRows type="NPC" title="Sold by" rows={data.soldBy} labelKey="name" idKey="id" collapsible click={r=>{close();jump({view:"shops",id:r.shopid})}}/>
      <LinkedRows type="GACHA" title="Available from gachapon" rows={data.gachapon} labelKey="location_code" idKey="npc_id" collapsible click={r=>{close();jump({view:"gacha",location:r.location_code})}}/>
      <LinkedRows type="CHARACTER" title="Owned by characters" rows={data.ownedBy} labelKey="name" idKey="id" collapsible defaultOpen={false} click={r=>{close();jump({view:"inventory",id:r.id})}} characterJump={(targetView,id)=>{close();jump({view:targetView,id})}}/></>}
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
  useEffect(()=>{if(query.trim().length<1){setRows([]);return}const t=setTimeout(async()=>{
    const suggestions=await api<Entity[]>(`/api/catalog/suggest?q=${encodeURIComponent(query)}&type=${type}&subtype=${subtype}`);
    if(suggestions.length){setRows(suggestions);return}
    const search=await api<Page<Entity>>(`/api/catalog/search?q=${encodeURIComponent(query)}&type=${type}&subtype=${subtype}&page=0&size=12&sort=name`);
    setRows(search.items);
  },160);return()=>clearTimeout(t)},[query,type,subtype]);
  return <div className="autocomplete">{value&&<div className="autocomplete-current"><EntityImage type={type} id={value.entity_id} properties={metadata(value.properties_json)}/><span><strong>{value.name}</strong><small>{value.entity_id}</small></span></div>}
    <SearchInput value={query} setValue={setQuery} placeholder={value?"Search to replace selection":placeholder}/>
    {rows.length>0&&<div className="suggestions">{rows.map(row=><button type="button" key={row.entity_id} onClick={()=>{onSelect(row);setRows([]);setQuery("")}}><EntityImage type={type} id={row.entity_id} properties={metadata(row.properties_json)}/><span><strong>{row.name}</strong><small>{row.entity_id} | {row.subtype}</small><em>{row.description}</em></span></button>)}</div>}</div>
}
function JobAutocomplete({value,onSelect}:{value:any|null;onSelect:(x:any)=>void}){
  const [query,setQuery]=useState("");const [jobs,setJobs]=useState<any[]>([]);
  useEffect(()=>{api<any[]>("/api/catalog/jobs").then(setJobs)},[]);
  const rows=query.trim()?jobs.filter(job=>String(job.job_id).includes(query)||String(job.job_name||"").toLowerCase().includes(query.toLowerCase())).slice(0,12):[];
  return <div className="autocomplete">{value&&<div className="autocomplete-current"><JobBadge jobId={Number(value.job_id||0)} skillId={Number(value.icon_skill_id||0)}/><span><strong>{value.job_name||`Job ${value.job_id}`}</strong><small>{value.job_id}</small></span></div>}
    <SearchInput value={query} setValue={setQuery} placeholder="Search job ID or name"/>
    {rows.length>0&&<div className="suggestions">{rows.map(row=><button type="button" key={row.job_id} onClick={()=>{onSelect(row);setQuery("")}}><JobBadge jobId={Number(row.job_id||0)} skillId={Number(row.icon_skill_id||0)}/><span><strong>{row.job_name}</strong><small>Job {row.job_id}</small><em>{row.source_path}</em></span></button>)}</div>}</div>
}
function AppearanceCatalogSelect({label,type,value,onSelect}:{label:string;type:"HAIR"|"FACE";value:number;onSelect:(entity:Entity)=>void}){
  const [selected,setSelected]=useState<Entity|null>(null);const [query,setQuery]=useState("");const [rows,setRows]=useState<Entity[]>([]);
  useEffect(()=>{if(value)api<Entity>(`/api/catalog/ITEM/${value}`).then(setSelected).catch(()=>setSelected({entity_type:"ITEM",entity_id:value,name:`${label} ${value}`,subtype:type}))},[type,value,label]);
  useEffect(()=>{if(!query.trim()){setRows([]);return}const timer=setTimeout(()=>api<Page<Entity>>(`/api/catalog/search?type=ITEM&subtype=${type}&q=${encodeURIComponent(query)}&page=0&size=12&sort=name`).then(page=>setRows(page.items)),160);return()=>clearTimeout(timer)},[type,query]);
  return <div className="appearance-selector"><label>{label}</label>{selected&&<button type="button" className="appearance-current" onClick={()=>setQuery(String(selected.entity_id))}><EntityImage type="ITEM" id={selected.entity_id}/><span><strong>{selected.name}</strong><code>{selected.entity_id}</code></span></button>}<SearchInput value={query} setValue={setQuery} placeholder={`Search ${label.toLowerCase()} id or name`}/>{rows.length>0&&<div className="appearance-results">{rows.map(row=><button type="button" key={row.entity_id} onClick={()=>{setSelected(row);setQuery("");setRows([]);onSelect(row)}}><EntityImage type="ITEM" id={row.entity_id}/><span><strong>{row.name}</strong><code>{row.entity_id}</code></span></button>)}</div>}</div>
}
function GenderIconSelect({value,onSelect}:{value:number;onSelect:(value:number)=>void}){
  return <label className="gender-icon-select"><span>Gender</span>
    <div className="gender-icon-options">{genderChoices.map(choice=>{
      const active=value===choice.id;
      return <button key={choice.id} type="button" className={`gender-choice ${active?"active":""}`} onClick={()=>onSelect(choice.id)}><span className="gender-symbol">{choice.icon}</span><span>{choice.name}</span></button>
    })}</div>
  </label>;
}
function ChoiceSelect({label,value,choices,onSelect}:{label:string;value:number;choices:{id:number;name:string;color?:string}[];onSelect:(choice:{id:number;name:string;color?:string})=>void}){
  const selected=choices.find(choice=>choice.id===value)||choices[0];
  return <label className="appearance-dropdown"><span>{label}</span><span className="appearance-dropdown-current"><span className="skin-swatch" style={{background:selected.color}}>{!selected.color&&<CircleUserRound size={18}/>}</span><select value={selected.id} onChange={event=>{const choice=choices.find(row=>row.id===Number(event.target.value));if(choice)onSelect(choice)}}>
    {choices.map(choice=><option key={choice.id} value={choice.id}>{choice.id} - {choice.name}</option>)}
  </select></span></label>
}
function StarterAppearancePicker({slot,label,gender,value,set}:{slot:StarterAppearanceSlot;label:string;gender:number;value:number;set:(itemId:number)=>void}){
  const options=starterAppearanceChoices[slot][gender===1?1:0];
  const [items,setItems]=useState<Record<number,Entity>>({});
  useEffect(()=>{Promise.all(options.map(itemId=>api<Entity>(`/api/catalog/ITEM/${itemId}`).then(item=>[itemId,item] as const).catch(()=>null))).then(rows=>setItems(Object.fromEntries(rows.filter(Boolean) as readonly (readonly [number,Entity])[])))},[options.join(",")]);
  useEffect(()=>{if(options.length&&!(options as readonly number[]).includes(value))set(options[0])},[options,value,set]);
  return <article className="starter-equip-picker starter-appearance-picker"><h4>{label}</h4><div className="starter-equip-options">{options.map(itemId=><button type="button" key={itemId} className={value===itemId?"selected":""} onClick={()=>set(itemId)}><EntityImage type="ITEM" id={itemId}/><span><strong>{items[itemId]?.name||`${label} ${itemId}`}</strong><small>{itemId}</small></span></button>)}</div></article>
}
function StarterEquipPicker({slot,label,gender,value,set}:{slot:StarterEquipSlot;label:string;gender:number;value:number;set:(itemId:number)=>void}){
  const options=starterEquipChoices[slot][gender===1?1:0];
  const [items,setItems]=useState<Record<number,Entity>>({});
  useEffect(()=>{Promise.all(options.map(itemId=>api<Entity>(`/api/catalog/ITEM/${itemId}`).then(item=>[itemId,item] as const).catch(()=>null))).then(rows=>setItems(Object.fromEntries(rows.filter(Boolean) as readonly (readonly [number,Entity])[])))},[options.join(",")]);
  useEffect(()=>{if(options.length&&!(options as readonly number[]).includes(value))set(options[0])},[options,value,set]);
  return <article className="starter-equip-picker"><h4>{label}</h4><div className="starter-equip-options">{options.map(itemId=><button type="button" key={itemId} className={value===itemId?"selected":""} onClick={()=>set(itemId)}><EntityImage type="ITEM" id={itemId}/><span><strong>{items[itemId]?.name||`Item ${itemId}`}</strong><small>{itemId}</small></span></button>)}</div></article>
}
function NumberField({label,value,set,onCommit,className="",detail,leftAction,rightAction,children}:{label:string;value:any;set:(value:number)=>void;onCommit?:(value:number)=>void;className?:string;detail?:string;leftAction?:ReactNode;rightAction?:ReactNode;children?:ReactNode}){const trailing=rightAction??children;const [startingValue,setStartingValue]=useState<number|null>(null);const [focused,setFocused]=useState(false);const [draft,setDraft]=useState(formatInteger(value));useEffect(()=>{if(!focused)setDraft(formatInteger(value))},[value,focused]);return <label className={className}>{label}<input type="text" inputMode="numeric" value={focused?draft:formatInteger(value)} onFocus={()=>{setFocused(true);setStartingValue(num(value));setDraft(String(num(value)))}} onChange={e=>{setDraft(e.target.value);set(parseNumberInput(e.target.value))}} onBlur={e=>{const next=parseNumberInput(e.target.value);setFocused(false);setDraft(formatInteger(next));if(startingValue===null||next!==startingValue)onCommit?.(next);setStartingValue(null)}} onKeyDown={e=>{if(e.key==="Enter"){e.preventDefault();e.currentTarget.blur()}}}/>{detail&&<small className="stat-detail">{detail}</small>}{(leftAction||trailing)&&<span className="stat-field-actions"><span>{leftAction}</span><span>{trailing}</span></span>}</label>}
function GmLevelSelect({value,set,onCommit,className=""}:{value:any;set:(value:number)=>void;onCommit?:(value:number)=>void;className?:string}){const current=Math.min(6,Math.max(0,num(value)));return <label className={`gm-level-select ${className}`.trim()}>GM level<select value={current} onChange={event=>{const next=Number(event.target.value);set(next);if(next!==current)onCommit?.(next)}}>{[0,1,2,3,4,5,6].map(level=><option key={level} value={level}>GM {level}</option>)}</select></label>}
function num(value:any){const parsed=Number(value);return Number.isFinite(parsed)?parsed:0}
function parseNumberInput(value:any){const parsed=Number(String(value??"").replace(/,/g,""));return Number.isFinite(parsed)?parsed:0}
function formatInteger(value:any){return parseNumberInput(value).toLocaleString()}
function spArray(value:any){const result=String(value??"0,0,0,0,0,0,0,0,0,0").split(",").map(x=>Math.max(0,num(x)));while(result.length<10)result.push(0);return result.slice(0,10)}
function skillBook(jobId:any){const id=Math.abs(num(jobId));if(id===0||id%1000===0)return 0;if(id%100===0)return 1;if(id%10===0)return 2;if(id%10===1)return 3;return 4}
function totalApBudget(level:number){return 25+Math.max(0,level-1)*5}
const MAX_HP_MP=300000;
function jobFamily(jobId:number){const id=Math.abs(jobId);if(id>=900)return"gm";if(id>=2100&&id<2200)return"aran";if((id>=500&&id<600)||(id>=1500&&id<1600))return"pirate";if((id>=200&&id<300)||(id>=1200&&id<1300))return"magician";if((id>=100&&id<200)||(id>=1100&&id<1200))return"warrior";if((id>=300&&id<500)||(id>=1300&&id<1500))return"ranged";return"beginner"}
function hpMpGain(jobId:number,kind:"hp"|"mp"){const family=jobFamily(jobId);if(family==="warrior")return kind==="hp"?26:5;if(family==="magician")return kind==="hp"?12:23;if(family==="ranged")return kind==="hp"?22:15;if(family==="pirate")return kind==="hp"?25:21;if(family==="aran")return kind==="hp"?46:7;if(family==="gm")return kind==="hp"?26:5;return kind==="hp"?14:11}
function averageHpMp(level:number,jobId:number,kind:"hp"|"mp"){const base=kind==="hp"?50:5;let total=base;for(let lv=2;lv<=Math.max(1,level);lv++)total+=lv<=10?hpMpGain(0,kind):hpMpGain(jobId,kind);return total}
function hpMpDetail(base:number,savedMax:number,equipBonus:number){const bonus=savedMax-base;const total=savedMax+equipBonus;return `${formatInteger(base)} ${bonus>=0?"+":"-"} ${formatInteger(Math.abs(bonus))} + ${formatInteger(equipBonus)} = ${formatInteger(total)}`}
function AutocompleteCharacter({value,onSelect}:{value:any;onSelect:(x:any)=>void}){
  const [query,setQuery]=useState("");const [rows,setRows]=useState<any[]>([]);
  useEffect(()=>{if(!query){setRows([]);return}const t=setTimeout(()=>api<any[]>(`/api/characters/search?query=${encodeURIComponent(query)}`).then(setRows),160);return()=>clearTimeout(t)},[query]);
  return <div className="autocomplete character-search"><SearchInput value={value?`${value.name} (#${value.id})`:query} setValue={v=>{setQuery(v);if(value)onSelect(null)}} placeholder="Search character IGN, account or ID"/>
    {rows.length>0&&!value&&<div className="suggestions">{rows.map(row=><button key={row.id} onClick={()=>{onSelect(row);setRows([])}}><CircleUserRound/><span><strong>{row.account_name} → {row.name}</strong><small>Lv. {row.level} | {row.job_name||"Unknown job"} ({row.job}) | #{row.id}</small></span></button>)}</div>}</div>
}

function InlineNumber({value,save,label}:{value:number;save:(v:number)=>void;label:string}){const [editing,setEditing]=useState(false);const [draft,setDraft]=useState(String(value));
  return <label className="inline-number"><span>{label}</span>{editing?<input autoFocus type="text" inputMode="numeric" value={draft} onChange={e=>setDraft(e.target.value)} onBlur={()=>{const next=parseNumberInput(draft);setEditing(false);if(next!==value)save(next)}} onKeyDown={e=>e.key==="Enter"&&e.currentTarget.blur()}/>:<button onClick={()=>{setDraft(String(value));setEditing(true)}}>{formatInteger(value)}</button>}</label>}
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
function gachaponChanceDisplay(row:any,tierCount?:number){
  if(row.enabled===false)return {percent:"0.00%",one:"Disabled"};
  const tier=Number(row.tier||0);
  const count=Number(tierCount||row.tier_count||0);
  const weights=[90,8,2];
  const percent=count>0?(weights[tier]||0)/count:0;
  return {percent:`${percent.toFixed(percent<1?3:2)}%`,one:percent>0?`1 in ${Math.max(1,Math.round(100/percent)).toLocaleString()}`:"1 in -"};
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
function JobBadge({jobId,skillId=0}:{jobId:number;skillId?:number}){const family=jobId===0?"BG":jobId<200?"WA":jobId<300?"MA":jobId<400?"BO":jobId<500?"TH":jobId<600?"PI":jobId>=1000&&jobId<2000?"CY":jobId>=2000?"HE":"GM";const iconId=skillId||defaultJobSkillIcon(jobId);return iconId?<EntityImage type="SKILL" id={iconId} className={`job-badge job-icon family-${family.toLowerCase()}`}/>:<span className={`job-badge family-${family.toLowerCase()}`}>{family}</span>}
function defaultJobSkillIcon(jobId:number){if(jobId===0)return 1000;const defaults:Record<number,number>={100:1000000,110:1100000,111:1110000,112:1120003,120:1200000,121:1210000,122:1220005,130:1300000,131:1310000,132:1320005,200:2000000,210:2100000,211:2110000,212:2121004,220:2200000,221:2210000,222:2221004,230:2300000,231:2310000,232:2321004,300:3000000,310:3100000,311:3110000,312:3120005,320:3200000,321:3210000,322:3220004,400:4000000,410:4100000,411:4110000,412:4120002,420:4200000,421:4210000,422:4220002,500:5000000,510:5100000,511:5110000,512:5121003,520:5200000,521:5210000,522:5220001};return defaults[jobId]||jobId*10000}
function MesoIcon({min=0,max=0}:{min?:number;max?:number}){const average=(Number(min||0)+Number(max||0))/2;const id=average>=1000?9000003:average>=100?9000002:average>=50?9000001:9000000;return <img className="meso-icon" src={`https://maplestory.io/api/GMS/83/item/${id}/icon`} alt="Meso drop" onError={hideImage}/>}
function SelectedEntity({entity}:{entity:Entity}){return <div className="selected-entity"><img src={assetUrl(entity.entity_type,entity.entity_id)} alt=""/><span><strong>{entity.name}</strong><code>{entity.entity_id}</code><small>{entity.description}</small></span></div>}
function StatStrip({ranges}:{ranges:Record<string,any>}){return <div className="stat-strip">{Object.entries(ranges).slice(0,4).map(([key,value]:any)=><span key={key}>{statName(key)} {value.average} ({value.min}-{value.max})</span>)}</div>}
function StatTable({ranges}:{ranges:Record<string,any>}){return <div className="stat-table">{Object.entries(ranges).map(([key,value]:any)=><div key={key}><strong>{statName(key)}</strong><span>{value.average}</span><small>{value.min} - {value.max}</small></div>)}</div>}
function LinkedRows({type,title,rows=[],labelKey,idKey,click,collapsible=false,defaultOpen=true,characterJump}:{type:string;title:string;rows:any[];labelKey:string;idKey:string;click:(x:any)=>void;collapsible?:boolean;defaultOpen?:boolean;characterJump?:(view:View,id:number)=>void}){
  const content=rows.length?<div className="linked-rows">{rows.map((row,i)=>{
    const chance=type==="GACHA"?gachaponChanceDisplay(row):row.chance==null?null:chanceDisplay(Number(row.chance));
    if(type==="CHARACTER")return <article className="linked-character-row" key={`${row[idKey]}-${i}`}>
      <button type="button" className="linked-character-main" onClick={()=>characterJump?characterJump("character-stats",Number(row[idKey])):click(row)}><span className="linked-icon"><CircleUserRound/></span><span className="linked-copy"><strong>{row[labelKey]||row[idKey]}</strong><code>{row[idKey]}</code>{row.account_name&&<small>{row.account_name}</small>}</span><ChevronRight size={14}/></button>
      <div className="character-dock-actions">
        <button type="button" onClick={()=>characterJump?.("inventory",Number(row[idKey]))}>Inventory / Storage</button>
        <button type="button" onClick={()=>characterJump?.("character-equipment",Number(row[idKey]))}>Equipment / Appearance</button>
      </div>
    </article>
  return <button key={`${row[idKey]}-${i}`} onClick={()=>click(row)}><span className="linked-icon">{type==="SHOP"?<Store/>:type==="GACHA"?(row[idKey]?<EntityImage type="NPC" id={row[idKey]}/>:<Ticket className="gacha-ticket-icon"/>):<EntityImage type={type} id={row[idKey]}/>}</span><span className="linked-copy"><strong>{type==="GACHA"?`Gachapon: ${gachaponTown(row[labelKey])}`:row[labelKey]||row[idKey]}</strong><code>{type==="GACHA"?`Tier ${row.tier}`:row[idKey]}</code>{row.region_name&&<small>{row.region_name} | {row.spawn_count} spawn points</small>}{chance&&<small className="linked-chance">{chance.percent} | {chance.one}</small>}</span><ChevronRight size={14}/></button>
  })}</div>:<p className="muted">No linked records.</p>;
  if(collapsible)return <details className="linked-section" open={defaultOpen}><summary>{title} <small>({rows.length})</small><ChevronRight size={16}/></summary>{content}</details>;
  return <section><h3>{title} <small>({rows.length})</small></h3>{content}</section>
}
function ConfirmDialog({title,message,confirmLabel="Confirm",onCancel,onConfirm}:{title:string;message:string;confirmLabel?:string;onCancel:()=>void;onConfirm:()=>void|Promise<void>}){
  const [busy,setBusy]=useState(false);
  async function confirm(){setBusy(true);try{await onConfirm()}finally{setBusy(false)}}
  return <div className="modal-backdrop confirm-backdrop" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title">
    <article className="modal-card confirm-card">
      <button type="button" className="modal-close" onClick={onCancel} disabled={busy}><X size={18}/></button>
      <div className="confirm-icon"><Trash2 size={22}/></div>
      <h2 id="confirm-dialog-title">{title}</h2>
      <p>{message}</p>
      <div className="modal-actions">
        <button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="button" className="danger-button confirm-delete-button" onClick={confirm} disabled={busy}><Trash2 size={15}/>{busy?"Deleting...":confirmLabel}</button>
      </div>
    </article>
  </div>
}
function RarityDialog({item,onCancel,onConfirm}:{item:Entity;onCancel:()=>void;onConfirm:(tier:number)=>void|Promise<void>}){
  const [tier,setTier]=useState(0);const [busy,setBusy]=useState(false);
  async function confirm(){setBusy(true);try{await onConfirm(tier)}finally{setBusy(false)}}
  return <div className="modal-backdrop confirm-backdrop" role="dialog" aria-modal="true" aria-labelledby="rarity-dialog-title">
    <article className="modal-card confirm-card rarity-card">
      <button type="button" className="modal-close" onClick={onCancel} disabled={busy}><X size={18}/></button>
      <SelectedEntity entity={item}/>
      <h2 id="rarity-dialog-title">Choose reward rarity</h2>
      <p>This decides which Gachapon pool the reward is added to.</p>
      <select className={`rarity-picker tier-${tier}`} value={tier} onChange={event=>setTier(Number(event.target.value))}>
        <option value={0}>Common</option>
        <option value={1}>Uncommon</option>
        <option value={2}>Rare</option>
      </select>
      <div className="modal-actions">
        <button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="button" className="primary" onClick={confirm} disabled={busy}>{busy?"Adding...":"Add reward"}</button>
      </div>
    </article>
  </div>
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
function itemTooltip(item:any){const lines=[item.item_name||String(item.itemid),`Quantity: ${item.quantity||1}`];const isEquip=Number(item.inventorytype)===1||Number(item.position)<0;if(isEquip){const stats=[["Upgrade slots",item.upgradeslots],["Upgrades",item.upgrades],["STR",item.str],["DEX",item.dex],["INT",item.int],["LUK",item.luk],["HP",item.hp],["MP",item.mp],["WATK",item.watk],["MATK",item.matk],["WDEF",item.wdef],["MDEF",item.mdef],["Accuracy",item.acc],["Avoidability",item.avoid],["Hands",item.hands],["Speed",item.speed],["Jump",item.jump],["Vicious",item.vicious],["Item level",item.itemlevel],["Item EXP",item.itemexp]];for(const [name,value] of stats)if(Number(value)!==0)lines.push(`${name}: ${value}`)}return lines.join("\n")}
function equipFrom(item:any){return {upgradeSlots:item.upgradeslots??0,level:item.upgrades??0,str:item.str??0,dex:item.dex??0,intStat:item.int??0,luk:item.luk??0,hp:item.hp??0,mp:item.mp??0,watk:item.watk??0,matk:item.matk??0,wdef:item.wdef??0,mdef:item.mdef??0,acc:item.acc??0,avoid:item.avoid??0,hands:item.hands??0,speed:item.speed??0,jump:item.jump??0,locked:item.locked??0,vicious:item.vicious??0,itemLevel:item.itemlevel??1,itemExp:item.itemexp??0}}

