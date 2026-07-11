# Interface

The mint-teal rewrite uses one persistent desktop shell around four fixed destinations: **Downloads**, **LinkGrabber**, **History**, and **Settings**.

| Region | Behavior |
| --- | --- |
| 52 px toolbar | Add Links, scheduler controls, contextual search, throughput, theme, clipboard monitoring, and window controls. |
| 208/72 px navigation rail | Collapses to an icon rail below 980 px; destinations remain fixed and do not become workspace tabs. |
| Dense page content | 62 px page headings, 48 px action rows, 34 px table headers, and 48 px data rows. |
| 30 px status bar | Aggregate speed, running count, remaining bytes, retry state, and the latest activity message. |
| 440 px Add Links drawer | Nonmodal URL entry with a scrim, initial focus, explicit close/cancel actions, and Escape dismissal. |

## Destinations

- **Downloads** is a package-to-file tree with state filters, contextual search, progress, speed, ETA, priority, completed-file actions, and queue-safe inline properties.
- **LinkGrabber** stages direct URLs while background HEAD or ranged-GET probes determine filename, size, redirects, and availability.
- **History** presents an append-only local timeline and preview for Downloads, LinkGrabber, and non-secret Settings snapshots. Undo, redo, and restore append new events.
- **Settings** uses a 220 px section list for General, Connection, Recovery, LinkGrabber, Appearance, Backup, and About.

Search follows Downloads, LinkGrabber, or History and is disabled in Settings. Transfer controls remain global, so changing destination never interrupts a probe, transfer, backup, or history write.

## Presentation and access

Light and dark themes switch immediately. English, playful Hong Kong Cantonese, and bilingual English / Hong Kong Cantonese also apply without stopping the engine. Status color is reinforced with text, icons, chips, progress, or values; keyboard focus remains visible; compact icon controls have tooltips; and the Add Links drawer receives initial focus and supports Escape.

The project does not claim complete screen-reader announcements or shortcut coverage for every dynamic queue change.

See all [21 application captures](Home#screenshot-gallery), try the [interactive demo](https://codingmachineedge.github.io/jdownloader-material/), or inspect the [design system](Design-System).
