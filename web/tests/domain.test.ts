import { describe, it, expect } from "vitest";
import raw from "../public/catalog.json";
import fixtures from "./android-fixtures.json";
import {
  exerciseSchema,
  defaultProfile,
  startSession,
  addExercise,
  nextSet,
  recordSet,
  skipExercise,
  previousSet,
  actualLoad,
  emptyData,
  dataSchema,
  index,
  type Catalog,
  type Profile,
} from "../src/model";
import { generate, assess } from "../src/generator";
const c = raw as unknown as Catalog;
const e = c.exercises.find((x) => x.id === "ez-curl")!;
describe("Catalog and generator parity", () => {
  it("validates 133 exercises", () => {
    expect(c.exercises).toHaveLength(133);
    for (const e of c.exercises)
      expect(exerciseSchema.safeParse(e).success).toBe(true);
  });
  for (const [i, fixture] of fixtures.entries())
    it(`matches Android golden case ${i + 1}`, () => {
      const result = generate(fixture.profile as Profile, {}, c);
      expect(
        result.days.map((d) => ({
          minutes: d.estimatedMinutes,
          items: d.exercises.map((p) => ({ id: p.exercise.id, sets: p.sets })),
        })),
      ).toEqual(fixture.days);
    });
  it("is deterministic, observes exclusions and time limits", () => {
    const p = { ...defaultProfile, sessionMinutes: 30 };
    const a = generate(
      p,
      { "ez-curl": "EXCLUDED", "barbell-bench": "EXCLUDED" },
      c,
    );
    expect(a).toEqual(
      generate(p, { "ez-curl": "EXCLUDED", "barbell-bench": "EXCLUDED" }, c),
    );
    expect(a.days.every((d) => d.estimatedMinutes <= 30)).toBe(true);
    expect(
      a.days
        .flatMap((d) => d.exercises)
        .some((p) => ["ez-curl", "barbell-bench"].includes(p.exercise.id)),
    ).toBe(false);
  });
  it("reports impossible constraints", () => {
    const p = { ...defaultProfile, equipment: [] };
    const plan = generate(p, {}, c);
    expect(
      assess(plan, p, c).warnings.some((w) => w.includes("нет доступных")),
    ).toBe(true);
  });
});
describe("Workout records", () => {
  it("free workout waits for manual finish, preserves rest and prevents duplicate records", () => {
    const s = addExercise(startSession(undefined, undefined, 1000), e),
      target = nextSet(s)!;
    const first = recordSet(s, target, 12, 20, 75, 2000);
    expect(first.completedAtEpochMillis).toBeNull();
    expect(first.completedSets).toHaveLength(1);
    expect(nextSet(first)).toBeNull();
    const second = addExercise(first, e);
    expect(nextSet(second)?.setNumber).toBe(2);
    expect(recordSet(second, target, 12, 20, 75, 200000)).toEqual(second);
    expect(recordSet(second, nextSet(second)!, 12, 20, 75, 2100)).toEqual(
      second,
    );
    expect(
      recordSet(second, nextSet(second)!, 10, 25, 75, 200000).completedSets,
    ).toHaveLength(2);
  });
  it("selects matching ordinal from most recent exercise execution", () => {
    const a = addExercise(startSession(undefined, undefined, 1000), e);
    const first = recordSet(a, nextSet(a)!, 12, 20, 75, 2000);
    const next = addExercise(first, e),
      end = recordSet(next, nextSet(next)!, 9, 25, 75, 200000),
      old = { ...end, completedAtEpochMillis: 210000 };
    const current = addExercise(startSession(), e);
    expect(previousSet([old], current, nextSet(current)!)?.repetitions).toBe(
      12,
    );
    const second = addExercise(
      recordSet(current, nextSet(current)!, 8, 20, 75),
      e,
    );
    expect(previousSet([old], second, nextSet(second)!)?.weightKg).toBe(25);
  });
  it("skips without inventing completed load; preserves partial sets", () => {
    const s = addExercise(startSession(undefined, undefined, 1000), e),
      first = recordSet(s, nextSet(s)!, 10, 30, 75, 2000),
      second = addExercise(first, e),
      skip = skipExercise(second, 200000);
    expect(skip.completedAtEpochMillis).toBeNull();
    expect(skip.completedSets).toHaveLength(1);
    const done = { ...skip, completedAtEpochMillis: 210000 };
    expect(actualLoad([done, done], c).BICEPS).toBe(1);
  });
  it("plan finishes automatically and mode-independent performance is stable", () => {
    const free = addExercise(startSession(undefined, undefined, 1000), e),
      plan = { ...free, mode: "PLANNED" as const };
    const done = recordSet(plan, nextSet(plan)!, 10, 30, 75, 2000);
    expect(done.completedAtEpochMillis).toBe(2000);
    expect(index(done.completedSets[0])).toBe(40);
  });
  it("validates backup and rejects malformed or duplicate sets", () => {
    const d = emptyData(),
      s = addExercise(startSession(), e);
    d.active = recordSet(s, nextSet(s)!, 10, 20, 75);
    expect(dataSchema.parse(JSON.parse(JSON.stringify(d)))).toEqual(
      dataSchema.parse(d),
    );
    d.active.completedSets.push(d.active.completedSets[0]);
    expect(dataSchema.safeParse(d).success).toBe(false);
  });
});
