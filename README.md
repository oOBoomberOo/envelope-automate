# Envelope: Automated Mailing

NeoForge 1.21.1 addon for [Envelope](https://github.com/mortuusars/Envelope) (built against 0.8.2) that automates mailing.

## Seal stamp fingerprints
Sneak + use any Envelope seal stamp to record **your** fingerprint on it (name + UUID, shown in the tooltip).
Doing it again on your own fingerprint wipes it; another player doing it overwrites it with theirs. Only the
player themselves can put their fingerprint on a stamp, so handing someone a fingerprinted stamp is handing them
access to your depository account. The fingerprint survives dyeing/washing the stamp. Names are resolved from the
UUID when used, so a renamed player's stamps keep working.

- **Mail Packager:** packages are sealed *as the fingerprinted player* (so deposits go to their account).
  Without a fingerprint, the packager's owner signs.
- **Depository Terminal:** use a stamp on the terminal to insert it (swaps out the previous one). With a
  fingerprint, the terminal shows and orders from that player's account, and the header turns blue. Orders are
  sealed with the stamp's look. Take the stamp out by clicking it in the GUI, or by sneak + using the terminal
  with an empty hand.

## Blocks

### Mail Packager
Place it next to a mailbox (any side counts as attached).

- **Packing:** pipes insert into an 18-slot packing buffer. On a **rising redstone edge** it takes up to one
  package's worth (6 stacks, partial stacks merged), makes a package addressed to the **Address Tag** in its
  config slot, seals it with the **Seal Stamp** in its config slot (optional, signed on behalf of the player
  who placed the packager), and puts it in the mailbox's outgoing slot for a courier to pick up.
  Nothing is used up: no paper box, no address tag, no stamp durability.
- **Continuous power:** while powered, it also packs automatically, whichever comes first:
  - the buffer holds a **full box** — all 6 package slots completely filled (a full stack each, or one item
    for stacks of one), so a steady item stream ships as full packages instead of waiting for it to stop;
  - or nothing has been inserted or removed for 1.5 seconds (`SETTLE_TICKS` = 30).

  Its own packing doesn't reset that timer, so a buffer with more than one package's worth keeps packing as each
  package gets posted.
  If the mailbox's outgoing slot is taken, the package waits in the packager's outgoing slot, and no new
  package is packed until it has been posted.
- **Regular / Direct Mail toggle:** the button left of the outgoing slot switches between packing regular
  packages and [Direct Mail](#direct-mail) packages. The setting is saved with the block.
- **Unpacking:** packages (sealed or unsealed, but not ones with a locked seal) arriving in the mailbox's inbox
  are pulled out and unpacked into an 18-slot output buffer, but only when all of their contents fit.
  Letters are left in the mailbox.
- **Pipes:** the item capability exposes the buffers. Input slots 0–17 are insert-only and output
  slots 18–35 are extract-only. This works from any side.

### Depository Terminal
Right-click to browse **your** Cloud Depository account (the account matches your seal signature, which is
your player name). Stored packages are opened up, so you browse and order individual items, much like
Create's Stock Ticker. Mail that isn't a browsable package (letters, loot packages) is listed and withdrawn
as-is.

- The view updates live while open: the server re-checks the account every 0.5 s and only sends an update when
  something changed (deposits, withdrawals, capacity, or changes from any other mod or command).
- Sort button next to the search box: **Quantity** (default), **Name**, or **Mod**. `@modid` in search filters by mod.
- Click an item to add it to the order: Shift adds a stack, Ctrl adds everything, right-click removes.
- **Deliver to**: any known address. Leave it empty to deliver back to the mailbox the order is sent from.
- **Send** creates a sealed **Withdrawal Order** letter addressed to the Cloud Depository.
  - When the terminal is attached to a mailbox (adjacent), the order is posted there automatically.
    The address field is prefilled with that mailbox.
  - Otherwise you get the letter and can post it from any mailbox.
- When the order arrives, the depository takes the items out of your account (reducing stored packages, and
  removing them once empty) and sends them in packages sealed by the depository to the destination.

## Direct Mail
Pale blue letters and packages that go **point-to-point** instead of through the sorting facility.

- **Items:** Direct Letter, Sealed Direct Letter, Direct Package, Sealed Direct Package. Craft any Envelope
  letter or package (sealed or not) with **light blue dye**; contents, address, and seal carry over. They open,
  seal, unseal, and unpack exactly like the regular ones. Placed in the world they use Envelope's regular
  letter/package block, but keep their identity when picked back up.
- **Sending:** put addressed Direct Mail in a mailbox's outgoing slot. Within half a second a **Direct Mail
  Pigeon** (a mailman pigeon, like the Mail Service's) appears in front of the mailbox, takes the mail and flies
  straight to the recipient's mailbox, then vanishes. No courier food is needed. It is Envelope's own pigeon with
  Envelope's flight AI, so it paths around obstacles, carries the mail on its back, and drops the mail if killed.
  Both mailboxes need a free block in front of them that a pigeon can fly to.
- **Limits:** a pigeon flies Direct Mail only to a **mailbox** (a player address resolves to that player's
  default mailbox) that is **loaded** and within **8 chunks** of the sender (a 17×17 chunk square around the
  sender's chunk).
- **Fallback to regular mail:** anything else — a service address such as the Mail Service or the Cloud
  Depository, an unknown address, or a mailbox that is too far away or not loaded — turns the Direct Mail back
  into its regular counterpart right in the outgoing slot (contents, address and seal kept). From there it is
  ordinary Envelope mail: it waits for a courier, needs courier food, goes through the sorting facility, and
  Envelope handles services and lost mail as usual.
- **In flight:** if the recipient's inbox turns out to be full on arrival, or the pigeon can't reach the mailbox
  in time, the pigeon carries the mail back to the sender's inbox marked *returned* (reason in the delivery log,
  Shift in the tooltip). A pigeon that ends up outside the simulated area hands the mail in immediately instead
  of flying the rest of the way.
- If the mailbox also holds courier food, a pigeon or bat can occasionally grab Direct Mail before its own pigeon shows up;
  it is then delivered the regular way.

## Automated loop
Collection system → pipes → **Mail Packager** (Address Tag: *Cloud Depository*, your Seal Stamp) → deposited
into your account. **Depository Terminal** → order to mailbox *X* → **Mail Packager** next to *X* unpacks →
pipes extract.

## Building
```
./gradlew build          # JDK 21; output in build/libs
./gradlew runClient      # dev client with Envelope on the classpath
```
Releases are built by [`.github/workflows/release.yml`](.github/workflows/release.yml): pushing a `v*` tag builds
the jar and publishes it as a GitHub release.
Dependencies (exact pins in `gradle.properties`): Envelope 0.8.2 from Modrinth maven (`6mpcJy5P`), and the
two libraries Envelope bundles: Mortaar 1.7.2 and Sable Companion 1.6.0.
