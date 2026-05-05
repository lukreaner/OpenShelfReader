# License decision

No license is selected yet. Decide before publishing a release or inviting outside contributors.

## Option: Apache-2.0

Good when:

- You want broad adoption.
- You want permissive reuse.
- You want lower friction for contributors and downstream apps.

Tradeoff:

- Someone can build a proprietary fork.

## Option: GPL-3.0

Good when:

- You want modified app distributions to remain open.
- You do not plan a network/server component.

Tradeoff:

- Some contributors and companies avoid GPL.

## Option: AGPL-3.0

Good when:

- You later add a server component and want network-use copyleft.
- You want stronger protection against hosted proprietary forks.

Tradeoff:

- Higher friction.
- Some mobile app ecosystems and contributors may dislike it.

## Recommendation for now

If the project remains app-only and avoids a dedicated sync server, Apache-2.0 is the friendliest default.

If a companion server becomes central later, revisit AGPL-3.0 with a proper ADR before writing server code.
