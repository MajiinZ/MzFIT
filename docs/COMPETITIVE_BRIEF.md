# MzFit training upgrade — November 17, 2026

## Scope and evidence

This comparison focuses on the everyday Android strength-training workflow: plan a session, log it efficiently, recover interrupted work and understand progress. It uses competitors' published feature descriptions, not hands-on benchmarks, customer interviews or comparative accuracy testing. It does not establish that MzFit outperforms these products.

| Area | Competitor evidence | MzFit before this upgrade | Implemented in this upgrade |
| --- | --- | --- | --- |
| Routine creation | [Hevy's guide](https://help.hevyapp.com/hc/en-us/articles/33106320824727-Everything-You-Need-to-Know-About-the-Hevy-App-2026-Features-Guide) describes creating routines | Fixed starter plans | Saved editable routines, library/custom exercises, order, targets and optional weekdays |
| Live logging | [Hevy's logging workflow](https://www.hevyapp.com/features/track-workouts/) includes live set logging | Exercise checklists plus journals edited after completion | Reps/load/timed sets during training, previous values, automatic rest timer and saved drafts |
| Progress | [Hevy's feature list](https://www.hevyapp.com/features/) emphasizes logging and progress tracking | Cumulative exercise totals and body-part counts | Calendar-based weekly goals, 28-day activity, heaviest recorded loads and external-load trends |
| Personalization | [Fitbod's workout explanation](https://help.fitbod.me/hc/en-us/articles/360004429814-How-Fitbod-Creates-Your-Workout) describes goals, experience, equipment and progression | Starter content and editable profile goal | User-authored routines/schedules; previous-performance guidance. No adaptive programming model is claimed |
| Social/community | [Hevy's social features](https://www.hevyapp.com/features/social-features/) describe sharing and community | None | None; this release prioritizes the private individual training workflow |

## Product decision

Strengthen the basic training loop before adding a social network, subscriptions or a broad AI-coach claim. A muscle map and pose overlay do not substitute for reliable workout logging. MzFit's useful product direction is local training records, visual movement education and transparent experimental pose checks. Its usefulness and commercial appeal still need validation with actual users.

The release adds custom routines and weekly schedules, durable active workouts, set-by-set input for repetitions or seconds, previous-set reuse, rest controls that remain visible during training, atomic completion and records derived from actual logged loads. It also aligns the UI with the user-supplied MzFit brand through navy/green colors, stronger typography and Today/Train/Progress bottom navigation. The hamburger menu retains access to the muscle map, ML coaching and tools.

## Boundaries and next release gates

- Android receives the new persistent studio. The iOS host remains uncompiled on this Windows machine; equivalent native studio storage/UI is not implemented there. Shared colors/header changes do carry across platforms.
- Pose rules remain experimental angle heuristics. No claim of universal correct form, injury prevention or real-world classifier accuracy is made.
- Schedules are local weekday assignments, not reminders or adaptive programs. Previous values are copied only on explicit request; there is no automatic prescription to increase load.
- No accounts, cloud sync, Health Connect integration, wearable app, payment/subscription flow, social graph or automated trainer assignment is included.
- Before a commercial release: validate real training sessions, accessibility/font scaling, process death/recovery, release signing/current store requirements, licenses, privacy disclosures and the actual store listing. Obtain user feedback before positioning MzFit as superior to established apps.
