# Pull dates: how ArrheniQ calculates them, and where it differs from eSTAR

Algorithm `schedule.pull-dates@1.0.0` (`scheduling.PullDateCalculator`). Requirements URS-SCH-001/002;
eSTAR rules BR-025 to BR-029 (`docs/discovery/business-rules.md`).

## The calculation

1. **Calendar date** = on-stability date + the time point:
   - months: same day of the month, or the last day of the month when the target month is shorter
     (31 Jan + 1 month = 28 Feb, or 29 Feb in a leap year; 31 Mar + 1 month = 30 Apr);
   - weeks: 7 days each; days: as given.
2. **Pull date**: the calendar date, unless it falls on a non-working day:
   - a **site holiday** follows the holiday rule (it wins over the weekend rule when a holiday is a Saturday or Sunday);
   - a **Saturday** or **Sunday** follows that day's rule;
   - rules: **leave** (keep the date), **move before** (the previous working day), **move after** (the next working day).
     A working day is Monday to Friday and not a site holiday, so a moved date never lands on a weekend or holiday.
3. The rules are tenant settings `scheduling.saturday_rule`, `scheduling.sunday_rule`, `scheduling.holiday_rule`,
   each overridable per site; holidays are the site's holiday calendar. New tenants get eSTAR's defaults:
   Saturday leave, Sunday move before (to Friday), holidays move after.

Time points are whole numbers of months, weeks or days. A holiday calendar with no working day within 31 days
of a date is refused as a configuration error.

## Differences from eSTAR

ArrheniQ's results were compared with a line-by-line port of eSTAR's calculation
(`NewStudyConditionData.mxml` `dateAddStandardIntervals`, `getNumberOfDaysInMonth`; `DateUtility.as`) over every
on-stability date of 2020–2030, time points 0–60 months, 1–13 weeks and 1–90 days, and all nine combinations of
the Saturday and Sunday rules: about 800,000 cases (`EstarParityTest`). They are identical except in two
situations, both eSTAR defects that the requirements ask us to correct. Any other difference fails the build.

| | Situation | eSTAR | ArrheniQ |
|---|---|---|---|
| **A** | Start day 29–31 and the target month is shorter (BR-026) | Rolls over into the next month: 31 Jan + 1M = **3 Mar**; 31 Mar + 1M = **1 May**; 31 Aug + 6M = **3 Mar** | Last day of the month: **28 Feb**, **30 Apr**, **28 Feb** |
| **B** | The result is 1 March in a non-leap year (BR-028) | Forced to **28 Feb** after the weekend move, even when 28 Feb is a Sunday (27 Feb 2020 + 12M with Saturday → Monday: eSTAR **Sun 28 Feb 2021**) | Keeps the correct date: **Mon 1 Mar 2021** |

About 0.6 % of the compared cases differ (A: 2,722; B: 1,683). For migrated studies, the dates eSTAR stored are
kept as they are; the engine only calculates new schedules.

Two more points, not visible in that comparison:

- **Site holidays** are new (eSTAR has no holiday calendar).
- **User-defined time points:** eSTAR doesn't apply the weekend rules to them (BR-029). ArrheniQ applies the same
  rules to every time point so that no pull falls on a weekend by accident. To confirm with the SME (question 1 below).

## Questions for the SME

1. Should time points added by hand to a study (not from the protocol) follow the weekend and holiday rules? ArrheniQ
   currently says yes; eSTAR said no.
2. Is “a holiday wins over the weekend rule” right? Example: Christmas on a Saturday, Saturday rule “leave”,
   holiday rule “move after” gives Monday.
3. Are fractional time points (1.5 months) ever used? They're refused now because they have no calendar meaning.
