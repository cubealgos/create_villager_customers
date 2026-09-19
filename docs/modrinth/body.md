# Modrinth listing (paste-ready)

## Project settings

| Field | Value |
|---|---|
| Name | Create Fly: Villager Customers |
| Slug | `villager-customers` |
| Summary | Villagers walk to your table-cloth shops during working hours and trade their own offers against your stock. |
| Categories | Utility, Adventure (secondary: Economy) |
| Licence | MIT |
| Client side | Unsupported |
| Server side | Required |
| Loaders | Fabric |
| Game versions | 26.2 |
| Dependencies | Create Fly (required), Fabric API (required) |
| Icon | `icon.png` in this folder: a villager at a table cloth on the round blueprint badge Create add-ons share (`just icon` regenerates it) |
| Links | Source `https://git.cubealgos.de/cubealgos/create_villager_customers` · Issues `https://github.com/cubealgos/create_villager_customers/issues` · Mirror `https://github.com/cubealgos/create_villager_customers` |

## Version settings

| Field | Value |
|---|---|
| Version number | `1.0.0+26.2` |
| Version title | Villager Customers 1.0.0 for Minecraft 26.2 |
| Channel | Release |
| File | `dist/create_villager_customers-1.0.0+26.2.jar` |
| Changelog | paste `dist/release-notes-1.0.0+26.2.md` |

## Body

Vanilla only lets a *player* walk to a villager. Villager Customers reverses one leg of that:
during their working hours, villagers with an offer they can still fulfil walk to your own
Create shop and execute that offer against it — buying goods out of the shop's stock with
emeralds, or selling goods into it for emeralds, whichever direction their own trade runs. You
build and stock the shop once; villagers keep the trade moving without being chased down one at
a time.

### What it does

- **A villager decides to go shopping.** During its working hours, each time its offers restock
  it has a 50% chance of deciding to wander to a shop at all. On success it searches its village
  for a table-cloth shop that mirrors one of its offers and walks there.
- **It executes its own offer, against the shop.** On arrival, the price goes into the linked
  stock ticker's payment box and the goods come straight out of the shop's network stock — one
  act, the same direction the villager's trade already ran. Nothing about the villager's trade
  itself changes: no new profession, no new offer, exactly as vanilla generated it.
- **The shop earns experience too.** Alongside the price, the trade's own xp lands in the payment
  box as experience nuggets, on top of the villager's usual trade xp.
- **Nearest shop wins.** If more than one shop in the village matches, the villager walks to the
  closest one.
- **Nothing else about villagers changes.** Panicking, sleeping, playing, raiding and every other
  vanilla activity are untouched; a village with no matching shop behaves exactly as before this
  mod is installed.

### Setting up a shop for villagers

Nothing to configure beyond what a shop already needs for a player: a table cloth with a price
and a request, a stock ticker linked to it with its keeper present, and stock sitting in the
network behind it. The only new step is remembering to empty the payment box — a villager's trade
fills it exactly as a player's checkout would.

### Made for Create

No new screen, no new block and no new item of its own: you read what happened through Create
Fly's own stock ticker and payment-box views, exactly as before this mod is installed.

### Privacy

Nothing leaves your machine. No telemetry, no update checks, no network calls of its own. The
only state this mod keeps of its own is two small memory values per villager.

### Requirements

Minecraft 26.2, Fabric, Fabric API, and Create Fly 6.0.9-1 (the build this version was tested
with; the mod declares exactly that version).

### Support

Through the issue tracker only (https://github.com/cubealgos/create_villager_customers/issues),
as time allows. Source on Forgejo, mirrored to GitHub. Include your Minecraft, Fabric and Create
Fly versions, the mod version from the jar name, and the steps that show the problem. MIT
licensed.
