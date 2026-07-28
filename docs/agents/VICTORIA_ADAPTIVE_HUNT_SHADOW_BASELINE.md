# Victoria Adaptive Hunt Shadow Baseline

Generated from the tested fixed runtime catalog and adaptive index revision `e745ae9bc900a22ffdb2eefc268ae00b2acd4659903be9494946b7c717fe970b`.

| Scope | Compared | Same first choice | Different first choice |
|---|---:|---:|---:|
| All fixed hunting objectives | 102 | 62 | 40 |
| Level 15 MVP quest-pack objectives | 23 | 11 | 12 |

A different prediction is evidence to review, not permission to replace a proven MVP route. The active policy remains PREFERRED_ADAPTIVE: fixed first, generated fallback only.

## MVP differences

| Quest | Objective | Fixed map | Predicted map | Score | Main evidence |
|---|---|---:|---|---:|---|
| 2082 The Stump Horror Story | 2082:kill:130100 | 101040000 | 101030400 Victoria Road: East Rocky Mountain I | 57250 | spawn=28000, concentration=40000, drops=0, irrelevantPenalty=1400 |
| 2088 The Reason Behind the Mushroom Studies | 2088:collect:4000001 | 103020200 | 100010100 Hidden Street: Nefarious Hill | 76156 | spawn=20000, concentration=50000, drops=12000, irrelevantPenalty=0 |
| 2089 I Need Help on My Homework! | 2089:collect:4000003 | 101040000 | 101030400 Victoria Road: East Rocky Mountain I | 74050 | spawn=28000, concentration=40000, drops=16800, irrelevantPenalty=1400 |
| 2091 I'm Bored 2 | 2091:collect:4000003 | 101040000 | 101030400 Victoria Road: East Rocky Mountain I | 74050 | spawn=28000, concentration=40000, drops=16800, irrelevantPenalty=1400 |
| 28268 [Hunt] The Pigs Are Ruining the Produce! | 28268:kill:1210100 | 104010000 | 120010000 Victoria Road: On the Way to the Harbor | 48721 | spawn=1000, concentration=50000, drops=0, irrelevantPenalty=0 |
| 28270 [Hunt] Pigs at the Corner | 28270:kill:1210100 | 104010000 | 120010000 Victoria Road: On the Way to the Harbor | 48721 | spawn=1000, concentration=50000, drops=0, irrelevantPenalty=0 |
| 28272 [Hunt] Intimidating Octopuses | 28272:kill:1120100 | 103030000 | 101020004 Victoria Road: Tree Dungeon, Forest Up North III | 48458 | spawn=19000, concentration=45240, drops=0, irrelevantPenalty=400 |
| 28273 [Collect] Eww, It's Slimy! | 28273:collect:4000004 | 100040100 | 101010101 Victoria Road: The Tree That Grew II | 105555 | spawn=53000, concentration=41405, drops=31800, irrelevantPenalty=2200 |
| 28276 [Hunt] Drowsiness from the Orange Mushrooms? | 28276:kill:1210102 | 104010000 | 103020200 Victoria Road: L Forest III | 48121 | spawn=35000, concentration=35715, drops=0, irrelevantPenalty=2800 |
| 28277 [Hunt] Camouflaging Slimes | 28277:kill:210100 | 100040100 | 101010101 Victoria Road: The Tree That Grew II | 73755 | spawn=53000, concentration=41405, drops=0, irrelevantPenalty=2200 |
| 28278 [Hunt] Destructively Strong Pigs | 28278:kill:1210100 | 104010000 | 120010000 Victoria Road: On the Way to the Harbor | 48721 | spawn=1000, concentration=50000, drops=0, irrelevantPenalty=0 |
| 28280 [Hunt] Sweep the Snails! | 28280:kill:100101 | 100010000 | 104000100 Victoria Road: Right Around Lith Harbor | 44968 | spawn=17000, concentration=31480, drops=0, irrelevantPenalty=2000 |

## Runtime evidence

With shadow mode enabled, each Agent/map selection emits one structured Agent hunt shadow log entry containing the fixed choice, adaptive prediction, catalog score, runtime-adjusted score, and score evidence. Repeated ticks with the same decision are deduplicated per Agent.
