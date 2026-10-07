import { z } from "zod";

export const groups = [
  "CHEST",
  "BACK",
  "SHOULDERS",
  "BICEPS",
  "TRICEPS",
  "QUADRICEPS",
  "HAMSTRINGS",
  "GLUTES",
  "CALVES",
  "CORE",
  "FOREARMS",
] as const;
export type Group = (typeof groups)[number];
export const equipmentKeys = [
  "BODYWEIGHT",
  "DUMBBELLS",
  "BARBELL",
  "BENCH",
  "CABLE",
  "MACHINE",
  "PULL_UP_BAR",
  "DIP_BARS",
  "EZ_BAR",
] as const;
const range = z.object({
  first: z.number().int().positive(),
  last: z.number().int().positive(),
});
export const exerciseSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  pattern: z.string(),
  equipment: z.array(z.enum(equipmentKeys)),
  minimumLevel: z.enum(["BEGINNER", "INTERMEDIATE", "ADVANCED"]),
  unilateral: z.boolean(),
  setupComplexity: z.number().nonnegative(),
  repRange: range,
  baseRestSeconds: z.number().int().min(0).max(1800),
  setDurationSeconds: z.number().positive(),
  warnings: z.array(z.string()),
  catalogVersion: z.number().int(),
  stimuli: z
    .array(
      z.object({
        region: z.string(),
        coefficient: z.number().min(0).max(1),
        role: z.enum(["PRIMARY", "SECONDARY", "STABILIZER"]),
      }),
    )
    .min(1),
});
export type Exercise = z.infer<typeof exerciseSchema>;
export const profileSchema = z.object({
  heightCm: z.number().int().min(100).max(250),
  weightKg: z.number().min(25).max(350),
  experience: z.enum(["BEGINNER", "INTERMEDIATE", "ADVANCED"]),
  daysPerWeek: z.number().int().min(1).max(6),
  sessionMinutes: z.number().int().min(30).max(120),
  splitPreference: z.enum(["AUTO", "FULL_BODY", "SPLIT"]),
  goal: z.enum(["HYPERTROPHY", "MAINTENANCE"]),
  equipment: z.array(z.enum(equipmentKeys)),
  priorityMuscles: z.array(z.enum(groups)),
  maintenanceMuscles: z.array(z.enum(groups)),
  painNotes: z.string().max(2000),
});
export type Profile = z.infer<typeof profileSchema>;
export const defaultProfile: Profile = {
  heightCm: 175,
  weightKg: 75,
  experience: "BEGINNER",
  daysPerWeek: 3,
  sessionMinutes: 60,
  splitPreference: "AUTO",
  goal: "HYPERTROPHY",
  equipment: [...equipmentKeys],
  priorityMuscles: [],
  maintenanceMuscles: [],
  painNotes: "",
};
export const prescriptionSchema = z.object({
  exercise: exerciseSchema,
  sets: z.number().int().min(1).max(999),
  reps: range,
  rir: z.number().int().min(0).max(10),
  restSeconds: z.number().int().min(0).max(1800),
  explanation: z.string(),
});
export type Prescription = z.infer<typeof prescriptionSchema>;
export const daySchema = z.object({
  id: z.string(),
  name: z.string(),
  focus: z.string(),
  exercises: z.array(prescriptionSchema).max(1000),
  estimatedMinutes: z.number().nonnegative(),
});
export type Day = z.infer<typeof daySchema>;
export const planSchema = z.object({
  id: z.string(),
  title: z.string(),
  days: z.array(daySchema).min(1).max(6),
  createdAtEpochMillis: z.number().nonnegative(),
});
export type Plan = z.infer<typeof planSchema>;
export const setSchema = z.object({
  exerciseId: z.string(),
  exerciseName: z.string(),
  exerciseIndex: z.number().int().nonnegative(),
  setNumber: z.number().int().positive(),
  repetitions: z.number().int().min(1).max(999),
  weightKg: z.number().min(0).max(10000),
  completedAtEpochMillis: z.number().nonnegative(),
  bodyweight: z.boolean(),
  userBodyWeightKg: z.number().min(0).max(350),
});
export type CompletedSet = z.infer<typeof setSchema>;
export const sessionSchema = z
  .object({
    id: z.string(),
    planId: z.string(),
    planTitle: z.string(),
    day: daySchema,
    startedAtEpochMillis: z.number().nonnegative(),
    completedAtEpochMillis: z.number().nonnegative().nullable(),
    completedSets: z.array(setSchema).max(10000),
    restEndsAtEpochMillis: z.number().nonnegative().nullable(),
    skippedExerciseIndices: z.array(z.number().int().nonnegative()),
    mode: z.enum(["PLANNED", "FREE"]),
  })
  .superRefine((s, ctx) => {
    const seen = new Set<string>();
    for (const set of s.completedSets) {
      const p = s.day.exercises[set.exerciseIndex];
      const key = `${set.exerciseIndex}-${set.setNumber}`;
      if (
        !p ||
        p.exercise.id !== set.exerciseId ||
        set.setNumber > p.sets ||
        seen.has(key)
      )
        ctx.addIssue({ code: "custom", message: "Повреждены записи подходов" });
      seen.add(key);
    }
    if (
      s.skippedExerciseIndices.some((i) => i >= s.day.exercises.length) ||
      (s.completedAtEpochMillis !== null &&
        s.completedAtEpochMillis < s.startedAtEpochMillis)
    )
      ctx.addIssue({ code: "custom", message: "Повреждена тренировка" });
  });
export type Session = z.infer<typeof sessionSchema>;
export const dataSchema = z
  .object({
    version: z.literal(1),
    profile: profileSchema,
    preferences: z.record(z.string(), z.enum(["FAVORITE", "EXCLUDED"])),
    plans: z.array(planSchema).max(500),
    activePlanId: z.string().nullable(),
    history: z.array(sessionSchema).max(10000),
    active: sessionSchema.nullable(),
  })
  .superRefine((data, ctx) => {
    if (
      data.history.some((s) => s.completedAtEpochMillis === null) ||
      (data.active && data.active.completedAtEpochMillis !== null) ||
      new Set(data.history.map((s) => s.id)).size !== data.history.length ||
      new Set(data.plans.map((s) => s.id)).size !== data.plans.length
    )
      ctx.addIssue({ code: "custom", message: "Неверная структура истории" });
  });
export type Data = z.infer<typeof dataSchema>;
export const emptyData = (): Data => ({
  version: 1,
  profile: structuredClone(defaultProfile),
  preferences: {},
  plans: [],
  activePlanId: null,
  history: [],
  active: null,
});
export interface Catalog {
  version: number;
  exercises: Exercise[];
  groups: Record<Group, string>;
  equipment: Record<string, string>;
  regions: Record<string, { title: string; group: Group }>;
}
export interface Media {
  sources: Record<string, string>;
  notes: Record<string, string>;
}
export const uid = () => crypto.randomUUID();
export function coefficients(
  e: Exercise,
  c: Catalog,
): Partial<Record<Group, number>> {
  const out: Partial<Record<Group, number>> = {};
  for (const s of e.stimuli) {
    const g = c.regions[s.region]?.group;
    if (g && s.role !== "STABILIZER")
      out[g] = Math.max(out[g] ?? 0, s.coefficient);
  }
  return out;
}
export const effort = (rir: number) =>
  rir <= 1 ? 1 : rir === 2 ? 0.9 : rir === 3 ? 0.8 : 0.65;
export function totals(items: Prescription[], c: Catalog) {
  const out = Object.fromEntries(groups.map((g) => [g, 0])) as Record<
    Group,
    number
  >;
  for (const p of items)
    for (const [g, v] of Object.entries(coefficients(p.exercise, c)))
      out[g as Group] += p.sets * effort(p.rir) * v!;
  return out;
}
export const seconds = (items: Prescription[]) =>
  !items.length
    ? 0
    : 300 +
      items.reduce(
        (n, p) =>
          n +
          p.sets *
            p.exercise.setDurationSeconds *
            (p.exercise.unilateral ? 2 : 1) +
          (p.sets - 1) * p.restSeconds +
          60 +
          p.exercise.setupComplexity * 15,
        0,
      );
export const effectiveWeight = (s: CompletedSet) =>
  s.bodyweight ? s.userBodyWeightKg : s.weightKg;
export const tonnage = (s: CompletedSet) => effectiveWeight(s) * s.repetitions;
export const index = (s: CompletedSet) =>
  effectiveWeight(s) * (1 + s.repetitions / 30);
export interface Target {
  exerciseIndex: number;
  setNumber: number;
  prescription: Prescription;
}
export function nextSet(s: Session): Target | null {
  if (s.completedAtEpochMillis !== null) return null;
  for (let i = 0; i < s.day.exercises.length; i++)
    if (!s.skippedExerciseIndices.includes(i)) {
      const p = s.day.exercises[i];
      for (let n = 1; n <= p.sets; n++)
        if (
          !s.completedSets.some(
            (x) => x.exerciseIndex === i && x.setNumber === n,
          )
        )
          return { exerciseIndex: i, setNumber: n, prescription: p };
    }
  return null;
}
export function startSession(
  day?: Day,
  plan?: Plan,
  now = Date.now(),
): Session {
  return {
    id: uid(),
    planId: plan?.id ?? "free",
    planTitle: plan?.title ?? "Свободная тренировка",
    day: day
      ? structuredClone(day)
      : {
          id: uid(),
          name: "Свободная тренировка",
          focus: "Ваш выбор",
          exercises: [],
          estimatedMinutes: 0,
        },
    startedAtEpochMillis: now,
    completedAtEpochMillis: null,
    completedSets: [],
    restEndsAtEpochMillis: null,
    skippedExerciseIndices: [],
    mode: day ? "PLANNED" : "FREE",
  };
}
export function addExercise(s: Session, e: Exercise): Session {
  if (s.mode !== "FREE" || s.completedAtEpochMillis !== null || nextSet(s))
    return s;
  const day = structuredClone(s.day),
    i = day.exercises.length - 1;
  if (
    day.exercises[i]?.exercise.id === e.id &&
    !s.skippedExerciseIndices.includes(i)
  )
    day.exercises[i].sets++;
  else
    day.exercises.push({
      exercise: e,
      sets: 1,
      reps: e.repRange,
      rir: 2,
      restSeconds: e.baseRestSeconds,
      explanation: "Выбрано вами",
    });
  return { ...s, day };
}
export function recordSet(
  s: Session,
  target: Target,
  repetitions: number,
  weightKg: number,
  bodyWeight: number,
  now = Date.now(),
): Session {
  const next = nextSet(s);
  if (
    !next ||
    next.exerciseIndex !== target.exerciseIndex ||
    next.setNumber !== target.setNumber ||
    s.completedAtEpochMillis !== null ||
    (s.restEndsAtEpochMillis ?? 0) > now ||
    !Number.isInteger(repetitions) ||
    repetitions < 1 ||
    repetitions > 999 ||
    !Number.isFinite(weightKg) ||
    weightKg < 0 ||
    weightKg > 10000
  )
    return s;
  const e = next.prescription.exercise,
    bodyweight = e.equipment.includes("BODYWEIGHT");
  const record: CompletedSet = {
    exerciseId: e.id,
    exerciseName: e.name,
    exerciseIndex: next.exerciseIndex,
    setNumber: next.setNumber,
    repetitions,
    weightKg: bodyweight ? 0 : weightKg,
    completedAtEpochMillis: now,
    bodyweight,
    userBodyWeightKg: bodyWeight,
  };
  const updated = { ...s, completedSets: [...s.completedSets, record] };
  const finished = s.mode === "PLANNED" && !nextSet(updated);
  return {
    ...updated,
    completedAtEpochMillis: finished ? now : null,
    restEndsAtEpochMillis: finished
      ? null
      : now + next.prescription.restSeconds * 1000,
  };
}
export function skipExercise(s: Session, now = Date.now()): Session {
  const next = nextSet(s);
  if (!next) return s;
  const updated = {
    ...s,
    skippedExerciseIndices: [...s.skippedExerciseIndices, next.exerciseIndex],
    restEndsAtEpochMillis: null,
  };
  return {
    ...updated,
    completedAtEpochMillis:
      s.mode === "PLANNED" && !nextSet(updated) ? now : null,
  };
}
export function previousSet(history: Session[], s: Session, t: Target) {
  const id = t.prescription.exercise.id,
    ordinal = s.completedSets.filter((x) => x.exerciseId === id).length;
  const last = history
    .filter(
      (w) =>
        w.completedAtEpochMillis !== null &&
        w.id !== s.id &&
        w.completedSets.some((x) => x.exerciseId === id),
    )
    .sort((a, b) => b.completedAtEpochMillis! - a.completedAtEpochMillis!)[0];
  return last?.completedSets
    .filter((x) => x.exerciseId === id)
    .sort(
      (a, b) => a.exerciseIndex - b.exerciseIndex || a.setNumber - b.setNumber,
    )[ordinal];
}
export function actualLoad(sessions: Session[], c: Catalog) {
  const out = Object.fromEntries(groups.map((g) => [g, 0])) as Record<
    Group,
    number
  >;
  for (const s of new Map(sessions.map((s) => [s.id, s])).values())
    if (s.completedAtEpochMillis !== null)
      for (const set of s.completedSets) {
        const e = s.day.exercises[set.exerciseIndex]?.exercise;
        if (e?.id === set.exerciseId)
          for (const [g, v] of Object.entries(coefficients(e, c)))
            out[g as Group] += v!;
      }
  return out;
}
export function matches(e: Exercise, q: string, c: Catalog) {
  const normal = (s: string) => s.toLowerCase().replaceAll("ё", "е");
  const hay = normal(
    [
      e.name,
      ...e.equipment.map((x) => c.equipment[x]),
      ...e.stimuli.map((x) => c.groups[c.regions[x.region]?.group]),
    ].join(" "),
  );
  return normal(q)
    .trim()
    .split(/\s+/)
    .every((t) => hay.includes(t));
}
