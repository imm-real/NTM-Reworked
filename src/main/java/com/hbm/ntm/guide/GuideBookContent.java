package com.hbm.ntm.guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The revised starter manual. Page copy lives beside the page definitions so
 * datagen and the client can never disagree about the book's structure.
 */
public final class GuideBookContent {
    private static final List<Page> PAGES;
    private static final Map<String, String> ENGLISH;

    static {
        List<Page> pages = new ArrayList<>();
        Map<String, String> english = new LinkedHashMap<>();

        english.put("item.hbm.book_guide", "Guide Book");
        english.put("item.hbm.book_guide.subtitle", "An Industrialist's Guide to Rebuilding Society");
        english.put("guide.hbm.cover.line1", "An Industrialist's");
        english.put("guide.hbm.cover.line2", "Guide to Rebuilding");
        english.put("guide.hbm.cover.line3", "Society");
        english.put("guide.hbm.cover.edition", "Expanded 1.21.1 Edition");
        english.put("guide.hbm.cover.open", "Click to open");
        english.put("guide.hbm.controls", "Arrows / wheel: turn pages   Home: cover");

        add(pages, english, "welcome", "Introduction",
                "Civilization fell over. Your job is to put the machines back. Start with ore and hand tools, then work toward steel, power, chemistry, transport, and precision parts. The chapters are in build order, more or less.",
                "hbm:book_guide");
        add(pages, english, "using_the_book", "Using This Guide",
                "Hover an icon for its item name. Turn pages with the arrows, mouse wheel, or left and right keys. Home jumps back to the cover. For ingredient counts, trust the recipe viewer and the machine's own selector.",
                "minecraft:book", "minecraft:crafting_table");
        add(pages, english, "contents_early", "Contents: Foundations",
                "5 Ores and strata • 6 Scanners and deep mining • 7 Material hazards • 8 Radiation • 9 Anvils • 10 Burner Press • 11 Fireclay • 12 Steel • 13 Heat • 14 Coke and Hot Air • 15 HE electricity • 16 Generators • 17 Assembly • 18 Blueprints and upgrades • 19 Circuits • 20 Silicon.",
                "hbm:survey_scanner", "hbm:anvil_iron", "hbm:machine_blast_furnace", "hbm:machine_assembly_machine");
        add(pages, english, "contents_industry", "Contents: Industry",
                "21 Conveyors • 22 Machine logistics • 23 Fluid tools • 24 Ducts and tanks • 25 Steam cycle • 26 Dense steam • 27 Oil • 28 Refining • 29 Fractioning • 30 Chemical industry • 31 Crucible • 32 Foundry transport • 33 Molds • 34 Advanced processing • 35 Pollution • 36 Explosives • 37–38 Build order • 39 Port scope • 40 Conclusion.",
                "hbm:conveyor_wand", "hbm:fluid_duct", "hbm:machine_well", "hbm:machine_crucible");

        add(pages, english, "ores", "Ores and Strata",
                "The world is deeper than it was in 1.7.10, so some deposits moved with the floor. Titanium, tungsten, cobalt, rare earth, uranium, sulfur, aluminium, and the rest all have jobs later. If an ore looks useless, keep it anyway.",
                "hbm:ore_titanium", "hbm:ore_tungsten", "hbm:ore_cobalt", "hbm:ore_rare");
        add(pages, english, "scanners", "Scanners and Deep Mining",
                "The Survey Scanner reports useful underground prospects; the Ore Density Scanner samples the bedrock mineral field. Density is coordinate-based, so move across X and Z rather than digging one endless shaft. Depth Rock resists ordinary tools: the Bismuth Pickaxe is the dependable early tool for it. Mark promising columns before committing machinery.",
                "hbm:survey_scanner", "hbm:ore_density_scanner", "hbm:bismuth_pickaxe");
        add(pages, english, "material_hazards", "Material Hazards",
                "Read hazard tooltips. Coal and asbestos dust damage lungs, hot items can ignite you, hydroactive materials dislike water, and explosive or blinding materials punish careless storage. Filters are consumed by masks according to their protection. A cheap improvised filter can save an early miner, but industrial hazards eventually demand proper protective equipment.",
                "hbm:gas_mask_filter_piss", "hbm:gas_mask_filter", "hbm:powder_asbestos", "hbm:powder_coal");
        add(pages, english, "radiation", "Radiation Safety",
                "A Geiger Counter measures current exposure and contaminated surroundings; a Dosimeter records accumulated dose. Distance, short visits, shielding, a complete Hazmat Suit, armor cladding, and Rad-X reduce risk. RadAway treats absorbed radiation after exposure but is not permission to stand beside hot material. Contaminated chunks persist and slowly spread radiation.",
                "hbm:geiger_counter", "hbm:hazmat_helmet", "hbm:radaway", "hbm:radx");

        add(pages, english, "anvils", "Industrial Anvils",
                "Your Iron or Lead Anvil is the first real workshop. Its construction, recycling, and smithing lists consume exact ingredients directly from the player inventory; the two visible slots handle ordered smithing. Higher-tier operations require a Steel Anvil. Use the search field and recipe pages instead of guessing shapes, and hold Shift where the interface offers bulk work.",
                "hbm:anvil_iron", "hbm:anvil_lead", "hbm:anvil_steel");
        add(pages, english, "press", "Burner Press and Stamps",
                "The Burner Press turns ingots and other stock into plates, wires, circuits, ammunition parts, and special shapes. Install the correct stamp, supply its own accepted fuel, and leave room for output. Stamps wear with use; their material controls durability. A Preheater can accelerate the machine. Circuit stamps are also essential when silicon production begins.",
                "hbm:machine_press", "hbm:press_preheater", "hbm:stamp_iron_flat", "hbm:stamp_iron_circuit");
        add(pages, english, "fireclay", "Fireclay and Firebrick",
                "The Blast Furnace is not made from ordinary bricks. Produce Fireclay from clay plus a valid aluminium or limestone route, then fire it into Firebrick. Build the furnace through the Tier-1 Anvil operation once you have its stonework, Firebricks, and Copper Plates. Save extra Firebrick: the Crucible and early foundry equipment use it later.",
                "hbm:ball_fireclay", "hbm:ingot_firebrick", "hbm:machine_blast_furnace");
        add(pages, english, "steel", "The Steel Bootstrap",
                "The basic Blast Furnace operation combines two Iron Ingots with one Sand to make two Steel Ingots and Slag. It burns only its supported fuel classes, not every vanilla furnace fuel. Hot Air Blast greatly increases speed, and every completed batch produces Flue Gas. Steel unlocks better anvils, machine bodies, cables, pipes, scaffolds, and most serious industry.",
                "minecraft:iron_ingot", "minecraft:sand", "hbm:ingot_steel", "hbm:machine_blast_furnace");

        add(pages, english, "heat", "Heat Is a Network",
                "Heat is separate from HE electricity. Fireboxes and Heating Ovens create thermal units; Ashpits collect useful ash; Stirling machines and other consumers draw heat through the correct neighboring face, usually from below. Hot machines cool toward their surroundings when idle. Build heat producers directly against consumers and watch temperature, transfer rate, smoke, and fuel quality.",
                "hbm:heater_firebox", "hbm:machine_ashpit", "hbm:machine_stirling", "hbm:heater_oven");
        add(pages, english, "coke_and_air", "Coke and Hot Air",
                "The Combination Oven uses an external heat source to coke coal, lignite, wood, or tar and can produce useful oils such as Coal Tar Creosote. For faster steel, an Air Intake consumes HE to supply ordinary Air; a heated Boiler converts Air into Hot Air Blast. Route that identified fluid to the Blast Furnace and route Flue Gas away.",
                "hbm:furnace_combination", "hbm:machine_intake", "hbm:machine_boiler", "hbm:fluid_duct");
        add(pages, english, "he_power", "HE Electricity",
                "NTM machines use HE, not Forge Energy. Red Copper Cable joins providers and receivers into a live network. A Battery Socket charges, buffers, or discharges finite Battery Packs according to its mode, redstone state, and priority. Keep generation and storage above a machine's HE-per-tick demand; low power resets progress in machines that require uninterrupted work.",
                "hbm:red_cable", "hbm:machine_battery_socket", "hbm:battery_pack");
        add(pages, english, "generators", "Choosing a Generator",
                "The Wood-Burning Generator is a compact first HE source. A heated Stirling Engine converts thermal power without a fluid loop. The Steam Engine turns Steam into Low-Pressure Steam, while Diesel handles refined fuel. The advanced Turbofan burns aviation-grade fuel such as Kerosene; power any one of its four ports to stop it, and stay clear of its intake.",
                "hbm:machine_stirling", "hbm:machine_steam_engine", "hbm:machine_diesel", "hbm:machine_turbofan");

        add(pages, english, "assembly", "Assembly Machine",
                "The Assembly Machine is the center of progression. Choose an operation in its recipe screen, then place each ingredient in the matching ghost lane. Supply HE and leave its output clear. The machine consumes inputs only when a batch completes, but blocked lanes or insufficient power reset progress. Its footprint exposes item, fluid, and HE access through lower proxy blocks.",
                "hbm:machine_assembly_machine", "hbm:blueprints", "hbm:red_cable");
        add(pages, english, "assembly_control", "Blueprints and Upgrades",
                "Blueprint pools reveal grouped Assembly and Chemical Plant operations. The selector search is often faster than paging. Speed increases throughput and power use; Power Saving reduces consumption; Overdrive is fast and extremely hungry. Upgrade levels add together only to their machine's cap. Do not install upgrades until generation and output handling can support them.",
                "hbm:blueprints", "hbm:upgrade_speed_1", "hbm:upgrade_power_1", "hbm:upgrade_overdrive_1");
        add(pages, english, "circuits", "Circuits and Soldering",
                "Vacuum tubes and simple circuit parts begin at the Press and Anvil. The Soldering Station combines exact solid lanes, HE, and—on advanced recipes—fluids such as Sulfuric Acid. Printed wafers, fine wire, polymer insulation, capacitors, and boards are distinct components, not interchangeable art variants. Hover recipe ingredients and match their exact names and subtypes.",
                "hbm:machine_soldering_station", "hbm:circuit", "hbm:wire_fine", "hbm:plate_polymer");
        add(pages, english, "silicon", "Silicon and Microchips",
                "The Electric Arc Furnace turns supported silica inputs into Silicon Nuggets. Combine them into Boules or unprinted Wafers, press a Wafer with a Circuit Stamp to print it, then use the Assembly Machine with Polymer and Gold Fine Wire to make a Microchip. An unprinted Silicon Wafer and a Printed Silicon Wafer are deliberately different items.",
                "hbm:machine_arc_furnace", "hbm:nugget_silicon", "hbm:billet_silicon", "hbm:circuit");

        add(pages, english, "conveyors", "Conveyors",
                "Conveyor wands place regular, express, double, or triple belts with source-correct straight and corner direction. Face the route carefully: belt arrows define travel. Chutes provide vertical handling. Loose conveyor items follow turns and slopes, and this edition also carries players and mobs standing on a belt. Express belts are fastest; wide families move more lanes, not arbitrary inventories.",
                "hbm:conveyor_wand", "hbm:conveyor_wand_express", "hbm:conveyor_wand_double", "hbm:conveyor_wand_triple");
        add(pages, english, "machine_logistics", "Extract, Insert, Package",
                "Machines do not throw outputs onto belts by themselves. Point a Crane Extractor at an exposed item-capability face to pull valid outputs onto a conveyor; use a Crane Inserter at the destination to push items into a valid input face. The Conveyor Boxer collects loose items into packages for transport and releases completed loads through the matching logistics path.",
                "hbm:crane_extractor", "hbm:crane_inserter", "hbm:crane_boxer", "hbm:conveyor_wand");
        add(pages, english, "fluid_tools", "Fluid Tools and Containers",
                "Sneak-use the Multi Fluid Identifier to open its compact searchable selector. Left click chooses the primary type and right click the secondary; normal use swaps them. Canisters carry supported liquids, Gas Tanks carry supported gases, and Universal Fluid Tanks carry one portable source fluid. Empty and full containers swap identities while preserving exactly 1,000 mB.",
                "hbm:fluid_identifier_multi", "hbm:canister_empty", "hbm:gas_empty", "hbm:fluid_tank_empty");
        add(pages, english, "ducts_and_tanks", "Ducts and Storage Tanks",
                "A Universal Fluid Duct is typed, has no internal buffer, and accepts only its selected fluid. Use an Identifier on one duct; sneak-use recursively retypes its connected region. Producers must actively push into the network. Placeable Storage Tanks hold much larger volumes and cycle input, buffer, output, and locked modes. Keep unlike fluids on separate typed networks.",
                "hbm:fluid_duct", "hbm:fluid_identifier_multi", "hbm:machine_fluidtank");

        add(pages, english, "steam_cycle", "Water and Steam Cycle",
                "Supply Water and heat to a Boiler to create ordinary Steam. A Steam Engine converts it into HE and Low-Pressure Steam. Route the exhaust to a Steam Condenser to recover Water, and use a groundwater Pump where the loop needs makeup water. Correct fluid identifiers and outward-facing machine ports matter more than simply touching two tanks together.",
                "hbm:machine_boiler", "hbm:machine_steam_engine", "hbm:machine_condenser", "hbm:pump_steam");
        add(pages, english, "dense_steam", "Dense Steam Grades",
                "Dense, Super Dense, and Ultra Dense Steam are separate hot fluid grades. The Heat Exchanging Heater cools each grade down one step while recovering heat; Dense Steam ultimately expands into ordinary Steam. The normal Boiler creates ordinary Steam only. Dense grades belong to later reactor paths, so this port currently supports transport and cooling without inventing an early producer.",
                "hbm:heater_heatex", "hbm:fluid_identifier_multi", "hbm:fluid_duct");
        add(pages, english, "oil", "Finding and Drilling Oil",
                "Ordinary Oil bubbles form deep above the modern world floor and leave a damaged surface scar near their center. An Oil Derrick drills downward, consumes HE, and extracts finite Crude Oil plus Natural Gas from the deposit. Keep its four ground-level cardinal spaces open for power and fluid connections. Deposits deplete; tank upgrades cannot create more oil.",
                "hbm:machine_well", "hbm:canister_empty", "hbm:gas_empty", "hbm:ore_oil");
        add(pages, english, "refinery", "Heating and Refining Oil",
                "First heat Crude Oil in a Boiler to make Hot Crude Oil. The Oil Refinery consumes Hot Oil and a small amount of HE, then atomically separates Heavy Oil, Naphtha, Light Oil, and Petroleum Gas. If any output is full, the whole operation stalls. Use four distinct typed outputs and keep the occasional Sulfur byproduct slot accessible.",
                "hbm:machine_boiler", "hbm:machine_refinery", "hbm:fluid_duct", "hbm:canister_empty");

        add(pages, english, "fractioning", "Fractioning and Cracking",
                "Fractioning Tower segments split normal oil fractions: Heavy Oil, Industrial Oil, Naphtha, and Light Oil lead to Bitumen, Heating Oil, Lubricant, Diesel, and Kerosene. Configure the bottom segment with an Identifier; stacked segments share the selected type unless separated by a Spacer. The Catalytic Cracking Tower opens additional Steam-assisted petroleum routes.",
                "hbm:machine_fraction_tower", "hbm:fraction_spacer", "hbm:machine_catalytic_cracker", "hbm:fluid_identifier_multi");
        add(pages, english, "chemistry", "Chemical Industry",
                "The Chemical Plant uses a selected recipe, exact item lanes, HE, and typed input/output tanks. Current operations cover Hydrogen, Peroxide, Sulfuric Acid, Rubber, Desh, Coltan cleaning, lubricants, concrete materials, batteries, electrodes, Cordite, and Dynamite where their dependencies exist. Centrifuging Redstone Ore supplies Mercury for the normal Desh chain; refined Diesel then powers a generator.",
                "hbm:machine_chemical_plant", "hbm:machine_centrifuge", "hbm:machine_diesel", "hbm:blueprints");
        add(pages, english, "crucible", "Crucible and Alloying",
                "Set a Crucible recipe, load the listed solids and Flux, then heat it from below. Inputs become measured molten material and waste; opposite spouts pour each stream downward. A blocked pour pauses transfer instead of deleting metal. Molten contents burn entities. Use a shovel to recover a bad batch as Scraps, which can be remelted without losing material.",
                "hbm:machine_crucible", "hbm:powder_flux", "hbm:scraps", "hbm:heater_firebox");
        add(pages, english, "foundry_transport", "Foundry Flow",
                "Pour directly into a Mold or Basin, or build a transport line. Channels carry small molten amounts, Storage Basins buffer larger batches, Outlets turn horizontal flow into a vertical pour, and Spill Outlets deliberately cast excess into Slag. Redstone, screwdriver inversion, and material filters control outlets. Never join channel networks already carrying different materials.",
                "hbm:foundry_channel", "hbm:foundry_tank", "hbm:foundry_outlet", "hbm:foundry_slagtap");

        add(pages, english, "molds", "Making and Using Molds",
                "Craft a Blank Mold, then use the correct Anvil demonstration recipe to imprint the desired shape. Demonstration items are not consumed. Shallow Foundry Molds accept small molds; Foundry Basins accept large batch molds. Insert the mold, fill its exact molten cost, and wait for cooling. Collect finished output by hand; use a Screwdriver to remove an empty installed mold.",
                "hbm:mold_base", "hbm:mold", "hbm:foundry_mold", "hbm:foundry_basin");
        add(pages, english, "advanced_fabrication", "Advanced Fabrication",
                "The Arc Welder joins compatible cast components into stronger parts. The Electric Arc Furnace handles silicon and other registered high-temperature solids. The Shredder converts supported ores and parts into powders or salvage, while the Centrifuge separates specific materials and fluids. Each machine exposes only its registered recipes: an empty selector means that input is unavailable, not hidden.",
                "hbm:machine_arc_welder", "hbm:machine_arc_furnace", "hbm:machine_shredder", "hbm:machine_centrifuge");
        add(pages, english, "pollution", "Smoke and Pollution",
                "Dirty combustion creates Soot and other pollution. Fireboxes, ovens, fluid burners, diesel generation, blocked exhaust, and several hot processes can contaminate an area. Pipe Smoke into a compatible sink when possible, avoid permanent industry beside living space, and stop a process before servicing it. Pollution can harm living things even when no radioactive material is present.",
                "hbm:heater_firebox", "hbm:heater_oilburner", "hbm:machine_diesel", "hbm:gas_mask_filter");
        add(pages, english, "explosives", "Explosives and Weapons",
                "Dynamite, TNT, Semtex, C4, shaped charges, conventional bombs, nuclear devices, and firearms are tools with real consequences. Fit fuses and timers deliberately, keep a Defuser available, and test far from industry. Procedural nuclear blasts can continue working over many ticks and leave radiation. A weapon or bomb appearing in creative does not make its ammunition or late ingredients early-game equipment.",
                "hbm:dynamite", "hbm:charge_dynamite", "hbm:defuser", "hbm:gun_pepperbox");

        add(pages, english, "build_order_one", "Recommended Build Order I",
                "1. Mine Copper, Iron, Aluminium, coal, sulfur, and specialty ores. 2. Make an Iron or Lead Anvil. 3. Build the Burner Press and durable flat/circuit stamps. 4. Produce Fireclay and construct the Blast Furnace. 5. Make Steel and a Steel Anvil. 6. Build Firebox/Ashpit heat, then stable HE generation, cable, and a Battery Socket.",
                "hbm:anvil_iron", "hbm:machine_press", "hbm:machine_blast_furnace", "hbm:machine_battery_socket");
        add(pages, english, "build_order_two", "Recommended Build Order II",
                "7. Construct the Assembly Machine and Shredder. 8. Establish fluid identifiers, ducts, and storage. 9. Add Soldering, Arc Furnace silicon, and Microchips. 10. Drill and refine Oil; make Rubber, Desh, and Diesel. 11. Build Crucible and foundry transport for cast parts. 12. Add automation and upgrades only after inputs, power, fluids, and outputs are reliable.",
                "hbm:machine_assembly_machine", "hbm:fluid_duct", "hbm:machine_refinery", "hbm:machine_crucible");
        add(pages, english, "port_scope", "What Is Not Here Yet",
                "Not everything made the trip yet. The Chicago Pile, enrichment cascade, SILEX/FEL, most later reactors and turbines, the bedrock Pumpjack route, and plenty of late machines are unfinished. A fluid name in creative does not mean its whole production chain works.",
                "hbm:book_guide");
        add(pages, english, "conclusion", "Rebuilding Society",
                "Label the pipes. Leave room to reach the machines. Keep power buffered and the radioactive junk away from the front door. Build one working line before copying it twelve times. Use the machinery well, or at least point the dangerous end away from home. Au revoir!",
                "hbm:book_guide", "hbm:steel_scaffold", "hbm:blowtorch", "hbm:geiger_counter");

        PAGES = List.copyOf(pages);
        ENGLISH = Collections.unmodifiableMap(new LinkedHashMap<>(english));
    }

    private GuideBookContent() {
    }

    private static void add(List<Page> pages, Map<String, String> english, String id,
                            String title, String body, String... iconIds) {
        String base = "guide.hbm.starter." + id;
        english.put(base + ".title", title);
        english.put(base + ".body", body);
        pages.add(new Page(id, base + ".title", base + ".body", List.of(iconIds)));
    }

    public static List<Page> pages() {
        return PAGES;
    }

    public static Map<String, String> english() {
        return ENGLISH;
    }

    public record Page(String id, String titleKey, String bodyKey, List<String> iconIds) {
        public Page {
            iconIds = List.copyOf(iconIds);
        }
    }
}
