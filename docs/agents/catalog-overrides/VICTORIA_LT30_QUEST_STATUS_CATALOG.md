# Victoria <30 Quest Status Catalog

This is the durable review/catalog layer for Victoria Island quests below level 30 after Maple Island. It records which quests are currently safe for autonomous scheduling, which are capability-gated, and which need review before an agent should pick them on its own.

Generated catalog: `docs/agents/catalog-overrides/victoria-lt30-quest-status.catalog.json`
Source policy: `docs/agents/VICTORIA_LT30_QUEST_EDGE_CASE_REVIEW.md`

## Summary

| Status | Count | Meaning |
| --- | ---: | --- |
| `manual-review-required` | 17 | Not ready for autonomous scheduling. |
| `normal` | 104 | Runnable with core navigation, NPC interaction, quest state, combat, loot, and inventory capabilities. |
| `postpone-outside-current-region` | 4 | Valid quest, but plan should postpone when objective leaves the current Victoria progression scope. |
| `requires-chain-source-review` | 12 | Needs quest item source or chain dependency review. |
| `requires-event-policy` | 7 | Seasonal/admin/special flow; disabled unless an event policy explicitly enables it. |
| `requires-field-boss-policy` | 1 | Runnable after field boss spawn, competition, wait, and retry policy exists. |
| `requires-jump-quest-nav-graph` | 3 | Runnable after jump-quest-specific route graphs and movement execution exist. |
| `requires-npc-placement-review` | 22 | Needs NPC placement, reachability, or script-source review. |
| `requires-pet-capability` | 1 | Runnable only for agents with pet systems enabled. |
| `requires-reactor-capability` | 3 | Runnable after agents can locate, hit/activate, and collect quest reactor outputs. |
| `requires-script-review` | 9 | Needs script or auto-complete behavior review. |

Runnable now with core capabilities: `104`
Runnable after known capability gates: `116`
Review required before autonomous scheduling: `67`

## Scheduling Rule

- Plan selectors may schedule `normal` quests once the core post-reconstruction capability set exists.
- Plan selectors may schedule `requires-reactor-capability`, `requires-jump-quest-nav-graph`, `requires-field-boss-policy`, `requires-pet-capability`, or `requires-pq-capability` only when the matching capability is enabled for that agent/profile/world.
- Plan selectors should postpone `postpone-outside-current-region` if the current plan is constrained to Victoria Island.
- Plan selectors must not autonomously schedule review-required statuses until their missing catalog evidence is resolved.

## Quest Index

### manual-review-required

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `1116` | 로웬의 사랑의 증표 |  | Rowen the Fairy (`1032101`) | Rowen the Fairy (`1032101`) | 4003005 x20 Soft Feather | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `2030` | Shumi's Request | 15 | Nella (`1052103`) | Nella (`1052103`) | 4003000 x5 Screw<br>4003001 x5 Processed Wood | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `2033` | Chris's Request | 25 | Nella (`1052103`) | Nella (`1052103`) | 4003004 x50 Stiff Feather<br>4000021 x20 Leather | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `2064` | Estelle's Request | 21 | Estelle (`1032105`) | Estelle (`1032105`) | 4021000 x1 Garnet | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `2105` | DANGER! <1-G. Mushroom> | 15 | Wanted : G. Mushroom (`1063003`) | The Rememberer (`1061011`) | 9101000 x99  | Mob source or current-map availability needs confirmation against map/mob catalog. |
| `2107` | DANGER! <3-Z. Mushroom> | 22 | Wanted : Z. Mushroom (`1063007`) | The Rememberer (`1061011`) | 9101001 x99  | Mob source or current-map availability needs confirmation against map/mob catalog. |
| `2111` | DANGER! <1-G. Mushroom> |  | Wanted : G. Mushroom (`1063003`) | The Rememberer (`1061011`) | 9101000 x999  | Mob source or current-map availability needs confirmation against map/mob catalog. |
| `2113` | DANGER! <3-Z. Mushroom> |  | Wanted : Z. Mushroom (`1063007`) | The Rememberer (`1061011`) | 9101001 x999  | Mob source or current-map availability needs confirmation against map/mob catalog. |
| `2156` | A rainbow snail shell that makes wishes come true!? | 20 | Pia (`1012102`) | Pia (`1012102`) | 2210006 x1 Rainbow-colored Snail Shell | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `2177` | Cleaning The Nautilus 2 | 25 | Mrs. Reade (`1092009`) | Mrs. Reade (`1092009`) | 4000095 x50 Rat Trap | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `2220` | Gathering Information Completed |  | JM From tha Streetz (`1052002`) | Lakelis (`9020000`) |  | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `2225` | The Cause and Effect |  | Reef (`1032107`) | Arwen the Fairy (`1032100`) |  | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `8048` | Arwen and Ellinia | 15 | Arwen the Fairy (`1032100`) | Arwen the Fairy (`1032100`) | 9101000 x100  | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `9414` | An Immovable Sword |  | Burnt Sword (`1022008`) | Burnt Sword (`1022008`) | 4001038 x1 Stump Eraser<br>4001039 x1 Mushmom Eraser<br>4001040 x1 Lupin Eraser<br>4001041 x1 Wraith Eraser<br>4001042 x1 Slime Eraser<br>4001043 x1 Octopus Eraser<br>4001115 x1 Undine's Cloth | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `9415` | Hectagon Crystal Necklace and the Seal Cushion |  | Arwen the Fairy (`1032100`) | Arwen the Fairy (`1032100`) | 4001038 x1 Stump Eraser<br>4001039 x1 Mushmom Eraser<br>4001040 x1 Lupin Eraser<br>4001041 x1 Wraith Eraser<br>4001042 x1 Slime Eraser<br>4001043 x1 Octopus Eraser<br>4001116 x1 Hectagon Necklace | Generated scan did not classify this quest as directly runnable; hold for manual review. |
| `28275` | [Hunt] You Were Bitten by a Green Mushroom? | 14 | Betty (`1032104`) | Betty (`1032104`) | 9101000 x40  | Mob source or current-map availability needs confirmation against map/mob catalog. |
| `28283` | For the peace of Victoria Island... | 25 | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 9400633 x1 Astaroth | Mob source or current-map availability needs confirmation against map/mob catalog. |

### normal

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `1115` | 마야의 사랑의 증표 |  | Maya (`1012101`) | Maya (`1012101`) | 4000001 x40 Orange Mushroom Cap | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `1117` | 이얀의 사랑의 증표 |  | Ayan (`1022007`) | Ayan (`1022007`) | 4000018 x40 Firewood | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `1118` | 넬라의 사랑의 증표 |  | Nella (`1052103`) | Nella (`1052103`) | 4000015 x40 Horny Mushroom Cap | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2000` | Fixing Blackbull's House | 10 | Blackbull (`1020000`) | Blackbull (`1020000`) | 4000018 x50 Firewood<br>4000003 x30 Tree Branch | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2002` | Maya of Henesys | 15 | Maya (`1012101`) | Teo (`1002001`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2003` | Finding Sophia | 15 | Teo (`1002001`) | Sophia (`1022100`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2004` | Making a Sparkling Rock | 15 | Sophia (`1022100`) | Manji (`1022002`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2005` | Arcon's Blood? | 15 | Manji (`1022002`) | Manji (`1022002`) | 4000008 x40 Charm of the Undead | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2009` | Pia and the Blue Mushroom | 20 | Pia (`1012102`) | Pia (`1012102`) | 4000009 x40 Blue Mushroom Cap<br>4000012 x40 Green Mushroom Cap | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2010` | Jane and the Wild Boar | 25 | Jane (`1002100`) | Jane (`1002100`) | 4000015 x100 Horny Mushroom Cap<br>4000020 x80 Wild Boar Tooth | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2014` | Alex's Request | 20 | Alex (`1052000`) | Chief Stan (`1012003`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2015` | Talking to Stan | 20 | Chief Stan (`1012003`) | Chief Stan (`1012003`) | 4000007 x30 Evil Eye Tail<br>4000002 x60 Pig's Ribbon | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2017` | Arwen and the Glass Shoe | 25 | Arwen the Fairy (`1032100`) | Arwen the Fairy (`1032100`) | 4001000 x1 Arwen's Glass Shoes | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2018` | Luke the Security Guy | 15 | Luke (`1040000`) | Luke (`1040000`) | 2020000 x1 Salad<br>4000034 x100 Jr. Necki Skin<br>4000042 x10 Stirge Wing | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2029` | Don Hwang's Request | 15 | Nella (`1052103`) | Nella (`1052103`) | 4000012 x50 Green Mushroom Cap<br>4000037 x50 Bubbling's Huge Bubble | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2031` | Andre's Request | 15 | Nella (`1052103`) | Nella (`1052103`) | 4000006 x100 Octopus Leg<br>2022000 x1 Pure Water | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2032` | Dr. Faymus's Request | 25 | Nella (`1052103`) | Nella (`1052103`) | 4000015 x100 Horny Mushroom Cap<br>4000034 x50 Jr. Necki Skin | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2034` | Cutthroat Manny's Request | 25 | Nella (`1052103`) | Nella (`1052103`) | 4000007 x150 Evil Eye Tail | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2062` | Mrs. Ming Ming's First Worry | 20 | Mrs. Ming Ming (`1012106`) | Mrs. Ming Ming (`1012106`) | 4000002 x100 Pig's Ribbon<br>4000010 x20 Slime Bubble<br>4000037 x50 Bubbling's Huge Bubble | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2063` | Mrs. Ming Ming's Second Worry | 21 | Mrs. Ming Ming (`1012106`) | Estelle (`1032105`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2065` | Searching for Fossils | 20 | Winston (`1022006`) | Winston (`1022006`) | 4031147 x100 Plant Fossil<br>4031146 x100 Animal Fossil | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2068` | Research on Animal Fossils | 22 | Betty (`1032104`) | Anne (`1012110`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2072` | Stranger's Identity | 25 | Jane Doe (`1052105`) | Jane Doe (`1052105`) | 4000008 x100 Charm of the Undead | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2077` | The Path of a Warrior |  | Olaf (`1002101`) | Dances with Balrog (`1022000`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2079` | The Path of a Thief |  | Olaf (`1002101`) | Dark Lord (`1052001`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2081` | A Lesson on Job Advancement | 8 | Olaf (`1002101`) | Olaf (`1002101`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2082` | The Stump Horror Story | 10 | Ayan (`1022007`) | Ayan (`1022007`) | 130100 x50 Stump | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2088` | The Reason Behind the Mushroom Studies | 10 | Bruce (`1012111`) | Bruce (`1012111`) | 4000001 x40 Orange Mushroom Cap<br>4000011 x10 Mushroom Spore | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2089` | I Need Help on My Homework! | 10 | Wing the Fairy (`1032106`) | Wing the Fairy (`1032106`) | 4000010 x10 Slime Bubble<br>4000003 x30 Tree Branch<br>4000004 x30 Squishy Liquid | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2090` | I'm Bored 1 | 10 | Icarus (`1052106`) | Icarus (`1052106`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2091` | I'm Bored 2 | 10 | Icarus (`1052106`) | Icarus (`1052106`) | 4000003 x40 Tree Branch<br>4000004 x40 Squishy Liquid | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2097` | A Spell that Seals Up a Critical Danger II |  | Insignificant Being (`1061012`) | Insignificant Being (`1061012`) | 4031213 x33 Wild Kargo's Spirit Rock<br>4031214 x18 Tauromacis's Spirit Rock<br>4031215 x18 Taurospear's Spirit Rock | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2103` | Nella's Dream | 10 | Nella (`1052103`) | Fanzy (`1040002`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2104` | Fanzy's Red Furball |  | Fanzy (`1040002`) | Fanzy (`1040002`) | 4031273 x1 Red Ball of Yarn | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2106` | DANGER! <2-H. Mushroom> | 20 | Wanted : H. Mushroom (`1063008`) | The Rememberer (`1061011`) | 2110200 x99 Horny Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2108` | POLLUTED! <1-Evil Eye> | 25 | Wanted : Evil Eye (`1063005`) | The Rememberer (`1061011`) | 2230100 x99 Evil Eye | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2112` | DANGER! <2-H. Mushroom> |  | Wanted : H. Mushroom (`1063008`) | The Rememberer (`1061011`) | 2110200 x999 Horny Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2114` | POLLUTED! <1-Evil Eye> |  | Wanted : Evil Eye (`1063005`) | The Rememberer (`1061011`) | 2230100 x999 Evil Eye | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2115` | POLLUTED! <2-Curse Eye> |  | Wanted : Curse Eye (`1063004`) | The Rememberer (`1061011`) | 3230100 x999 Curse Eye | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2116` | Planting Trees | 20 | Winston (`1022006`) | Winston (`1022006`) | 4000195 x54 Seedling | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2118` | Eliminate Monsters from the Site |  | Winston (`1022006`) | Winston (`1022006`) | 2230111 x300 Rocky Mask<br>2230110 x200 Wooden Mask | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2122` | Manji and the Secret Group | 18 | Manji (`1022002`) | Manji (`1022002`) | 2130100 x10 Dark Axe Stump | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2127` | To the Desert... | 18 | Manji (`1022002`) | Manji (`1022002`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2145` | Stump Research |  | Winston (`1022006`) | Winston (`1022006`) | 4031773 x100 Dry Branch | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2147` | 스텀피의 묘목 기르기 |  | Winston (`1022006`) | Winston (`1022006`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2148` | Truth of the Rumor-Blackbull |  | Blackbull (`1020000`) | Blackbull (`1020000`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2149` | Truth of the Rumor-Manji |  | Manji (`1022002`) | Manji (`1022002`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2150` | Truth of the Rumor-Ayan |  | Ayan (`1022007`) | Ayan (`1022007`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2151` | Truth of the Rumor- Dances with Balrog |  | Dances with Balrog (`1022000`) | Dances with Balrog (`1022000`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2152` | Truth of the Rumor-Betty |  | Betty (`1032104`) | Betty (`1032104`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2154` | Hero of the Story | 20 | Mrs. Ming Ming (`1012106`) | Wing the Fairy (`1032106`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2155` | Snail Hunt | 20 | Wing the Fairy (`1032106`) | Wing the Fairy (`1032106`) | 4000000 x10 Blue Snail Shell<br>4000016 x10 Red Snail Shell<br>4000019 x10 Snail Shell | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2165` | The Prince's Request | 10 | Sharyl (`1092003`) | Sharyl (`1092003`) | 2000006 x1 Mana Elixir | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2166` | Shining Stone | 10 | Sharyl (`1092003`) | Sharyl (`1092003`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2171` | Can you please give Baine a helping hand? | 10 | Baine (`1092002`) | Bart (`1094000`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2173` | The Truth Behind the Black Shadow | 10 | Baine (`1092002`) | Baine (`1092002`) | 4031846 x1 Black Magician's Token<br>130101 x20 Red Snail<br>1210100 x20 Pig | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2176` | Cleaning The Nautilus 1 | 15 | Mrs. Reade (`1092009`) | Mrs. Reade (`1092009`) | 4000002 x50 Pig's Ribbon | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2178` | Cleaning The Nautilus 3 |  | Mrs. Reade (`1092009`) | Jack (`1092010`) | 2022000 x1 Pure Water | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2179` | Protect The Nautilus' Emergency Food Supply | 15 | Tangyoon (`1092000`) | Tangyoon (`1092000`) | 1210100 x30 Pig<br>1210101 x30 Ribbon Pig | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2197` | Tienk, the Monster Book Salesman |  | Tienk (`2006`) | Tienk (`2006`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2204` | Strange Dish 1 | 25 | Tangyoon (`1092000`) | Tangyoon (`1092000`) | 4000007 x40 Evil Eye Tail | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2207` | Lazy Little Calico | 25 | Bartol (`1092011`) | Calico (`1092004`) | 4000007 x10 Evil Eye Tail | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2209` | Bring a Lemon for Shulynch |  | Bartol (`1092011`) | Shulynch (`1092008`) | 2010004 x1 Lemon | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2214` | The Run-down Huts in the Swamp |  | JM From tha Streetz (`1052002`) | Knocked Trash Can (`1052108`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2215` | Find the Crumpled Piece of Paper Again |  | JM From tha Streetz (`1052002`) | Knocked Trash Can (`1052108`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2223` | The Name He Calls |  | Fanzy (`1040002`) | Fanzy (`1040002`) | 4031925 x10 Cursing Nail | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2229` | A Way to Fight Off Sleep | 23 | Mike (`1040001`) | Mike (`1040001`) | 2000000 x1 Red Potion<br>2000001 x1 Orange Potion<br>2000002 x1 White Potion<br>2000003 x1 Blue Potion | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2231` | Captain Al, Who Are You? | 10 | Captain Al (`1002103`) | Captain Al (`1002103`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2246` | Horny Mushroom Caps Research | 24 | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 4000015 x30 Horny Mushroom Cap | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2247` | Raging Horny Mushrooms |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 2110200 x80 Horny Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2248` | Understanding the Horny Mushroom Signals |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 4032390 x20 Tainted Horny Mushroom Cap | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2249` | Zombie Mushroom Signal 1 |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 2230101 x80 Zombie Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2250` | Zombie Mushroom Signal 2 |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 4000008 x100 Charm of the Undead | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2251` | Zombie Mushroom Signal 3 |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 4032399 x20 Recording Charm | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2252` | Studying the Mushroom Signals 1 (Repeatable) |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 2230101 x30 Zombie Mushroom<br>2110200 x20 Horny Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `2253` | Studying the Mushroom Signals 2 (Repeatable) |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 2230131 x20 Annoyed Zombie Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `3049` | Estelle and the Syrup |  | Estelle (`1032105`) | Estelle (`1032105`) | 4000029 x60 Lupin's Banana<br>4000017 x10 Pig's Head<br>2012002 x30 Sap of Ancient Tree | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `4647` | The Secret Method |  | Trainer Bartos (`1012006`) | Trainer Bartos (`1012006`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `6000` | The Wandering Alchemist Eurek's New Skill | 13 | Eurek the Alchemist (`2040050`) | Eurek the Alchemist (`2040050`) | 2040600 x1 Scroll for Bottomwear for DEF<br>1302013 x1 Red Whip<br>2040000 x1 Scroll for Helmet for DEF<br>2043000 x1 Scroll for One-Handed Sword for ATT<br>2040400 x1 Scroll for Topwear for DEF | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `8084` | Sophia's Snack | 18 | Sophia (`1022100`) | Sophia (`1022100`) | 2012002 x20 Sap of Ancient Tree | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28136` | The Wandering Alchemist Eurek's New Skill |  | Eurek the Alchemist (`2040050`) | Eurek the Alchemist (`2040050`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28257` | Who Broke the Seal of Andras? |  | Dances with Balrog (`1022000`) | Dances with Balrog (`1022000`) | 4001367 x1 Ripped Paper-1 | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28258` | Who Broke the Seal of Valefor? |  | Dark Lord (`1052001`) | Dark Lord (`1052001`) | 4001368 x1 Ripped Paper-2 | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28260` | Who Broke the Seal of Crocell? |  | Kyrin (`1090000`) | Kyrin (`1090000`) | 4001370 x1 Ripped Paper-4 | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28262` | Revealed Identity |  | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 4001371 x1 Ripped Paper-5<br>4001367 x1 Ripped Paper-1<br>4001368 x1 Ripped Paper-2<br>4001369 x1 Ripped Paper-3<br>4001370 x1 Ripped Paper-4 | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28263` | The Rememberer's Training-1 | 25 | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 2110200 x50 Horny Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28264` | The Rememberer's Training-2 | 25 | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 2230101 x50 Zombie Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28265` | The Rememberer's Training-3 | 25 | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 2230100 x50 Evil Eye | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28266` | Secret of Astaroth | 25 | The Rememberer (`1061011`) | The Rememberer (`1061011`) |  | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28267` | [Collect] I Need an Umbrella! | 14 | Rina (`1010100`) | Rina (`1010100`) | 4000012 x20 Green Mushroom Cap | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28268` | [Hunt] The Pigs Are Ruining the Produce! |  | Camila (`1012108`) | Camila (`1012108`) | 1210100 x30 Pig | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28269` | [Hunt] The Terrorizing Red Ribbon Pigs | 12 | Jay (`1012109`) | Jay (`1012109`) | 1210101 x40 Ribbon Pig | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28270` | [Hunt] Pigs at the Corner |  | Nella (`1052103`) | Nella (`1052103`) | 1210100 x30 Pig | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28271` | [Hunt] That Red Isn't For Everyone! | 12 | Alex (`1052000`) | Alex (`1052000`) | 1210101 x30 Ribbon Pig | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28272` | [Hunt] Intimidating Octopuses | 12 | Icarus (`1052106`) | Icarus (`1052106`) | 1120100 x30 Octopus | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28273` | [Collect] Eww, It's Slimy! |  | Wing the Fairy (`1032106`) | Wing the Fairy (`1032106`) | 4000004 x20 Squishy Liquid | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28274` | [Hunt] Why Are Dark Stumps So Dark? | 12 | Rowen the Fairy (`1032101`) | Rowen the Fairy (`1032101`) | 1110101 x40 Dark Stump | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28276` | [Hunt] Drowsiness from the Orange Mushrooms? |  | Calico (`1092004`) | Calico (`1092004`) | 1210102 x30 Orange Mushroom | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28277` | [Hunt] Camouflaging Slimes |  | Bartol (`1092011`) | Bartol (`1092011`) | 210100 x30 Slime | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28278` | [Hunt] Destructively Strong Pigs |  | Bonnie (`1092001`) | Bonnie (`1092001`) | 1210100 x30 Pig | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28279` | [Collect] Red Ribbons Around the Pig's Neck | 12 | Rolonay (`1092012`) | Rolonay (`1092012`) | 4000002 x20 Pig's Ribbon | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28280` | [Hunt] Sweep the Snails! |  | Ayan (`1022007`) | Ayan (`1022007`) | 100101 x10 Blue Snail<br>130101 x10 Red Snail | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28281` | [Collect] Preparations for the Traditional Ceremony | 12 | Blackbull (`1020000`) | Blackbull (`1020000`) | 4000005 x20 Leaf | Deemed runnable by current scan and policy once core agent capabilities exist. |
| `28282` | How to Avoid the Stink | 25 | The Rememberer (`1061011`) | The Rememberer (`1061011`) | 4001372 x30 Wild Boar Leather<br>4000008 x30 Charm of the Undead<br>4001373 x30 Evil Eye Eyeball | Deemed runnable by current scan and policy once core agent capabilities exist. |

### postpone-outside-current-region

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `2075` | Find the Maple History Book 2 | 25 | Tigun the Advisor (`2041022`) | Jay (`1012109`) | 4031160 x1 Medal of Honor | Completable later, but outside the current Victoria progression region. |
| `2254` | Karcasa of the Desert | 25 | The Rememberer (`1061011`) | Karcasa (`2101013`) |  | Completable later, but outside the current Victoria progression region. |
| `8053` | Traveling Around Maple 1 |  | Olaf (`1002101`) | Riel (`1081100`) |  | Completable later, but outside the current Victoria progression region. |
| `8059` | Traveling Around Maple 7 |  | Jr. Officer Medin (`2050009`) | Olaf (`1002101`) |  | Completable later, but outside the current Victoria progression region. |

### requires-chain-source-review

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `2006` | Getting Arcon's Blood | 15 | Manji (`1022002`) | Sophia (`1022100`) | 4000006 x20 Octopus Leg<br>4031005 x1 Arcon's Blood<br>4000004 x50 Squishy Liquid<br>4000005 x50 Leaf | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2007` | Making Sparkling Rock | 15 | Sophia (`1022100`) | Teo (`1002001`) | 4031004 x1 Sparkling Rock | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2008` | Delivering the Weird Medicine | 15 | Teo (`1002001`) | Maya (`1012101`) | 4031006 x1 Weird Medicine | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2016` | Mother's Gold Watch | 20 | Chief Stan (`1012003`) | Alex (`1052000`) | 4031007 x1 Old Gold Watch | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2066` | Delivering a Box of Fossil | 20 | Winston (`1022006`) | Betty (`1032104`) | 4031148 x1 Winston's Recommendation<br>4031149 x1 Fossil Box | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2069` | Transporting Drake's Skull | 22 | Anne (`1012110`) | Betty (`1032104`) | 4031151 x1 Stuffed Drake Skull | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2070` | Progress on Fossil Research | 23 | Betty (`1032104`) | Winston (`1022006`) | 1032000 x1 Weighted Earrings<br>4000018 x50 Firewood<br>4031152 x1 Fossil Report<br>4031153 x1 Stump's Teardrop<br>4000005 x100 Leaf | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2073` | Camila's Gem | 23 | Camila (`1012108`) | Utah (`1012107`) | 4031156 x1 Sparkling Glass Marble | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2076` | Estelle's Special Sauce | 21 | Estelle (`1032105`) | Mrs. Ming Ming (`1012106`) | 4000006 x60 Octopus Leg<br>4000017 x20 Pig's Head<br>4031154 x1 Estelle's Special Sauce | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2092` | I Need to Find My Daughter 1 | 10 | Bruce (`1012111`) | Ayan (`1022007`) | 4031174 x1 Ayan's Toy Sword | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2093` | I Need to Find My Daughter 2 |  | Ayan (`1022007`) | Bruce (`1012111`) | 4031173 x1 Ayan's Letter | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |
| `2153` | The Old Snail | 20 | Pia (`1012102`) | Jay (`1012109`) | 4161035 x1 The Legend of Snail | Quest item source or chain dependency needs catalog confirmation before autonomous scheduling. |

### requires-event-policy

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `8700` | 2006 Easter : Easter Basket | 8 | Maple Administrator (`9010000`) | Maple Administrator (`9010000`) | 4000003 x100 Tree Branch<br>4000004 x100 Squishy Liquid | Seasonal/admin/special flow; keep disabled unless an event policy enables it. |
| `8800` | Anniversary : Birthday Present (Red) |  | Maple Administrator (`9010000`) | Maple Administrator (`9010000`) | 4031306 x1 Birthday Present (Red) | Seasonal/admin/special flow; keep disabled unless an event policy enables it. |
| `8801` | Anniversary : Cody's Quest |  | Cody (`9200000`) | Cody (`9200000`) | 4031305 x10 Birthday Candle | Seasonal/admin/special flow; keep disabled unless an event policy enables it. |
| `8804` | Independence Day : Cody's Barbecue Party |  | Cody (`9200000`) | Cody (`9200000`) | 4000017 x50 Pig's Head<br>4000003 x50 Tree Branch<br>4000021 x50 Leather | Seasonal/admin/special flow; keep disabled unless an event policy enables it. |
| `8821` | Thanksgiving : Turkey Yellow Egg hunt | 15 | Cody (`9200000`) | Cody (`9200000`) | 4031416 x1 Yellow Turkey Egg | Seasonal/admin/special flow; keep disabled unless an event policy enables it. |
| `8833` | New Year's Wishes 1 |  | Maple Administrator (`9010000`) | auto/none |  | Seasonal/admin/special flow; keep disabled unless an event policy enables it. |
| `8834` | New Year's Wishes 2 |  | Maple Administrator (`9010000`) | auto/none |  | Seasonal/admin/special flow; keep disabled unless an event policy enables it. |

### requires-field-boss-policy

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `2146` | Monstrous Tree Stumpy |  | Winston (`1022006`) | Winston (`1022006`) | 3220000 x1 Stumpy | Needs field-boss spawn, competition, and wait/retry policy. |

### requires-jump-quest-nav-graph

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `2050` | Sabitrama and the Diet Medicine | 25 | Sabitrama (`1061005`) | Sabitrama (`1061005`) | 4031020 x1 Pink Anthurium | Requires a jump-quest route graph and movement executor before normal plan cards should schedule it. |
| `2052` | John's Pink Flower Basket | 15 | John (`20000`) | John (`20000`) | 4031025 x10 Pink Viola | Requires a jump-quest route graph and movement executor before normal plan cards should schedule it. |
| `2055` | Shumi's Lost Coin | 20 | Shumi (`1052102`) | Shumi (`1052102`) | 4031039 x1 Shumi's Coin | Requires a jump-quest route graph and movement executor before normal plan cards should schedule it. |

### requires-npc-placement-review

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `2078` | The Path of a Bowman |  | Olaf (`1002101`) | Athena Pierce (`1012100`) |  | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2080` | The Path of a Magician |  | Olaf (`1002101`) | Grendel the Really Old (`1032001`) |  | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2123` | A Special Assignment | 18 | Manji (`1022002`) | Moppie (`2012019`) |  | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2162` | The Half-written Letter | 10 | Muirhat (`1092007`) | Athena Pierce (`1012100`) | 4031839 x1 Crumpled Letter | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2164` | Athena Pierce's Gift | 10 | Athena Pierce (`1012100`) | Kyrin (`1090000`) | 4031840 x1 Old Orgel | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2167` | Sea Firefly1 | 25 | Sharyl (`1092003`) | Muse (`2060006`) | 4031841 x1 Magic Flask (Empty) | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2168` | Sea Firefly2 | 25 | Muse (`2060006`) | Sharyl (`1092003`) | 4031842 x1 Magic Flask (Filled) | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2172` | What is it that Bart saw? | 10 | Bart (`1094000`) | Baine (`1092002`) | 4031845 x1 Daily Log | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2174` | Report to Muirhat | 10 | Baine (`1092002`) | Muirhat (`1092007`) | 4031862 x1 Black Magician's Token<br>4031863 x1 Confidential Report | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2175` | Disciples of the Black Magician | 10 | Muirhat (`1092007`) | Muirhat (`1092007`) | 9300156 x1 Black Magician's Disciple | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2180` | Find Fresh Milk | 20 | Tangyoon (`1092000`) | Tangyoon (`1092000`) | 4031850 x1 Milk Jug (Full) | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2181` | Porchay's Letter | 20 | Porchay (`1092013`) | Kenta (`2060005`) | 4031858 x1 Porchay's Letter | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2183` | A Banquet for the Whalians | 20 | Kenta (`2060005`) | Porchay (`1092013`) | 4031851 x1 Whalean Canned Food | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2185` | Dress for Kyrin |  | Mrs. Ming Ming (`1012106`) | Black Bark (`1092006`) | 4031852 x1 Dress | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2186` | Help Me Find My Glasses | 15 | Abel (`1094001`) | Abel (`1094001`) | 4031853 x1 Abel's Glasses  | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2210` | Take the Gold Pouch to Muirhat |  | Bartol (`1092011`) | Muirhat (`1092007`) | 4031891 x1 Gold Pouch | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2211` | Deliver the Tattered Map to Black Bark |  | Bartol (`1092011`) | Black Bark (`1092006`) | 4031892 x1 Tattered Map | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2221` | Conclusion |  | JM From tha Streetz (`1052002`) | JM From tha Streetz (`1052002`) | 6220000 x1 Dyle | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2224` | Rage, Resentment, and Revenge |  | Fanzy (`1040002`) | Reef (`1032107`) |  | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2226` | Arwen's Apology |  | Arwen the Fairy (`1032100`) | Reef (`1032107`) | 4031924 x1 Fairy Hair | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2230` | A Mysterious Small Egg | 15 | Mar the Fairy (`1032102`) | Mar the Fairy (`1032102`) | 4032086 x1 Mysterious Small Egg | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |
| `2264` | Mr. Lim and the Subway | 25 | Jake (`1052006`) | Mr. Lim (`1052115`) |  | NPC placement/reachability or script source needs confirmation before autonomous scheduling. |

### requires-pet-capability

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `4646` | Pet Instructor Test |  | Trainer Bartos (`1012006`) | Trainer Bartos (`1012006`) | 4031921 x1 Hidden note | Pet-specific quest; keep out of normal progression unless pet behavior is enabled. |

### requires-reactor-capability

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `2067` | Research on Plant Fossils | 22 | Betty (`1032104`) | Betty (`1032104`) | 4031150 x20 Plant Sample | Quest-specific reactor drop: reactor 1012000 -> 4031150. |
| `2074` | Find the Maple History Book | 25 | Jay (`1012109`) | Tigun the Advisor (`2041022`) | 4031157 x1 Maple History Book I<br>4031158 x1 Maple History Book II<br>4031159 x1 Maple History Book III | Quest-specific reactor drops: 9102000 -> 4031157, 9102001 -> 4031158; also crosses outside ordinary Victoria routing.<br>secondary: postpone-outside-current-region |
| `2169` | The Large Pearl | 25 | Sharyl (`1092003`) | Muirhat (`1092007`) | 4031843 x1 Large Pearl | Quest-specific reactor drop: reactor 1202002 -> 4031843. |

### requires-script-review

| Quest | Name | Min Lv | Start | Complete | Requirements | Notes |
| ---: | --- | ---: | --- | --- | --- | --- |
| `2071` | Stranger's Request | 25 | Jane Doe (`1052105`) | auto/none | 4031155 x20 Broken Mirror Glass | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2117` | Shawn the Excavator's Request | 25 | Winston (`1022006`) | auto/none | 4000197 x5 Slate<br>4000196 x5 Wooden Board | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2216` | Information from Mr. Pickall |  | Mr. Pickall (`9000008`) | auto/none |  | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2217` | Information from Shumi |  | Shumi (`1052102`) | auto/none |  | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2218` | Information from Nella |  | Nella (`1052103`) | auto/none |  | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2219` | Information from Jake |  | Jake (`1052006`) | auto/none |  | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2232` | Find a Junior! |  | Captain Al (`1002103`) | auto/none |  | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2233` | Raise the Rep! |  | Captain Al (`1002103`) | auto/none |  | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
| `2234` | Enjoy the Entitlement! |  | Captain Al (`1002103`) | auto/none |  | Script-only or auto-complete behavior needs confirmation before autonomous scheduling. |
