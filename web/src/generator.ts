import {
  groups,
  coefficients,
  effort,
  totals,
  seconds,
  type Group,
  type Profile,
  type Prescription,
  type Catalog,
  type Plan,
  type Exercise,
} from "./model";
const levels = ["BEGINNER", "INTERMEDIATE", "ADVANCED"];
const floors = [
  "UPPER_CHEST",
  "MID_CHEST",
  "LATS",
  "UPPER_BACK",
  "SIDE_DELTS",
  "REAR_DELTS",
];
const compound = [
  "HORIZONTAL_PUSH",
  "VERTICAL_PUSH",
  "HORIZONTAL_PULL",
  "VERTICAL_PULL",
  "SQUAT",
  "HIP_HINGE",
  "LUNGE",
];
export function target(p: Profile, g: Group) {
  return (
    [6, 8, 10][levels.indexOf(p.experience)] *
    (["CORE", "CALVES", "FOREARMS"].includes(g) ? 0.65 : 1) *
    (p.goal === "MAINTENANCE" || p.maintenanceMuscles.includes(g)
      ? 0.55
      : p.priorityMuscles.includes(g)
        ? 1.5
        : 1)
  );
}
function focuses(p: Profile): { title: string; groups: readonly Group[] }[] {
  const all = { title: "Всё тело", groups };
  if (
    p.splitPreference === "FULL_BODY" ||
    p.daysPerWeek === 1 ||
    (p.splitPreference === "AUTO" && p.daysPerWeek <= 3)
  )
    return Array.from({ length: p.daysPerWeek }, () => all);
  const legs: Group[] = ["QUADRICEPS", "HAMSTRINGS", "GLUTES", "CALVES"];
  const upper = {
      title: "Верх тела",
      groups: groups.filter((g) => !legs.includes(g)),
    },
    lower = { title: "Ноги · кор", groups: [...legs, "CORE"] as Group[] };
  const chest = {
    title: "Грудь · трицепс · плечи",
    groups: ["CHEST", "TRICEPS", "SHOULDERS", "CORE", "CALVES"] as Group[],
  };
  const back = {
    title: "Спина · бицепс · плечи",
    groups: ["BACK", "BICEPS", "SHOULDERS", "FOREARMS", "CALVES"] as Group[],
  };
  return p.daysPerWeek === 2
    ? [upper, lower]
    : p.daysPerWeek === 3
      ? [chest, back, lower]
      : p.daysPerWeek === 4
        ? [upper, lower, upper, lower]
        : p.daysPerWeek === 5
          ? [chest, back, lower, upper, lower]
          : [chest, back, lower, chest, back, lower];
}
const allowed = (
  e: Exercise,
  p: Profile,
  preferences: Record<string, string>,
) =>
  e.catalogVersion <= 3 &&
  e.equipment.every((x) => p.equipment.includes(x)) &&
  levels.indexOf(e.minimumLevel) <= levels.indexOf(p.experience) &&
  preferences[e.id] !== "EXCLUDED";
export function assess(plan: Plan, p: Profile, c: Catalog) {
  const items = plan.days.flatMap((d) => d.exercises),
    load = totals(items, c),
    warnings: string[] = [];
  const coverage = groups.map((group) => ({
    group,
    value: Math.round(load[group] * 10) / 10,
    min: target(p, group),
    max: target(p, group) + 2,
  }));
  for (const item of coverage) {
    if (item.value + 0.25 < item.min)
      warnings.push(`${c.groups[item.group]}: ниже недельной цели`);
    if (item.value > item.max + 0.05)
      warnings.push(`${c.groups[item.group]}: превышен целевой объём`);
  }
  for (const region of floors) {
    const n = items.reduce(
      (n, p) =>
        n +
        p.sets *
          effort(p.rir) *
          Math.max(
            0,
            ...p.exercise.stimuli
              .filter((s) => s.region === region && s.role !== "STABILIZER")
              .map((s) => s.coefficient),
          ),
      0,
    );
    if (n + 0.05 < target(p, c.regions[region].group) * 0.25)
      warnings.push(
        `${c.regions[region].title}: недостаточное покрытие отдела`,
      );
  }
  for (const day of plan.days) {
    if (!day.exercises.length)
      warnings.push(`${day.name}: нет доступных упражнений`);
    if (seconds(day.exercises) > p.sessionMinutes * 60)
      warnings.push(`${day.name}: превышен лимит времени`);
  }
  if (p.painNotes.trim())
    warnings.push(
      "Отмечена боль или ограничение. Согласуйте нагрузку со специалистом.",
    );
  return { coverage, warnings };
}

/** Direct port of Android's bounded weekly marginal-gain search. Stable catalog ordering breaks ties. */
export function generate(
  p: Profile,
  preferences: Record<string, string>,
  c: Catalog,
): Plan {
  const available = c.exercises
    .filter((e) => allowed(e, p, preferences))
    .sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const focus = focuses(p),
    days: Prescription[][] = Array.from({ length: p.daysPerWeek }, () => []);
  const targets = Object.fromEntries(
    groups.map((g) => [g, target(p, g)]),
  ) as Record<Group, number>;
  const coeff = Object.fromEntries(
    available.map((e) => [e.id, coefficients(e, c)]),
  );
  const rir = 3 - levels.indexOf(p.experience);
  for (let iteration = 0; iteration < 240; iteration++) {
    const week = totals(days.flat(), c),
      times = days.map(seconds),
      local = days.map((d) => totals(d, c));
    const regional: Record<string, number> = {};
    for (const item of days.flat())
      for (const s of item.exercise.stimuli)
        if (s.role !== "STABILIZER")
          regional[s.region] =
            (regional[s.region] ?? 0) +
            item.sets * effort(item.rir) * s.coefficient;
    let best: { day: number; items: Prescription[]; score: number } | null =
      null;
    for (let day = 0; day < days.length; day++)
      for (const e of available) {
        const primary = e.stimuli
          .filter((s) => s.role === "PRIMARY")
          .sort((a, b) => b.coefficient - a.coefficient)[0];
        if (
          !primary ||
          !focus[day].groups.includes(c.regions[primary.region].group)
        )
          continue;
        const current = days[day],
          existing = current.findIndex((x) => x.exercise.id === e.id);
        if (
          (existing < 0 && current.length >= 10) ||
          (existing >= 0 && current[existing].sets >= 4)
        )
          continue;
        const add = existing < 0 ? 2 : 1,
          delta = Object.entries(coeff[e.id]).map(
            ([g, v]) => [g as Group, v! * add * effort(rir)] as const,
          );
        if (
          delta.some(
            ([g, v]) =>
              week[g] + v > targets[g] + 2 ||
              local[day][g] + v >
                Math.max(
                  4,
                  targets[g] /
                    Math.max(
                      1,
                      focus.filter((f) => f.groups.includes(g)).length,
                    ) +
                    2,
                ),
          )
        )
          continue;
        const item =
          existing >= 0
            ? { ...current[existing], sets: current[existing].sets + 1 }
            : {
                exercise: e,
                sets: 2,
                reps: e.repRange,
                rir,
                restSeconds: e.baseRestSeconds,
                explanation: "",
              };
        const updated =
            existing >= 0
              ? current.map((x, i) => (i === existing ? item : x))
              : [...current, item],
          duration = seconds(updated);
        if (duration > p.sessionMinutes * 60) continue;
        let benefit = delta.reduce(
          (n, [g, v]) =>
            n +
            (p.priorityMuscles.includes(g) ? 1.6 : 1) *
              (Math.max(0, 1 - week[g] / targets[g]) ** 2 -
                Math.max(0, 1 - (week[g] + v) / targets[g]) ** 2),
          0,
        );
        for (const s of e.stimuli)
          if (floors.includes(s.region) && s.role !== "STABILIZER") {
            const t = targets[c.regions[s.region].group] * 0.25,
              n = regional[s.region] ?? 0;
            benefit +=
              Math.max(0, 1 - n / t) ** 2 -
              Math.max(0, 1 - (n + add * effort(rir) * s.coefficient) / t) ** 2;
          }
        if (benefit <= 0.0001) continue;
        const primaries = e.stimuli.filter((s) => s.role === "PRIMARY");
        benefit *=
          1 +
          (0.12 *
            primaries.reduce(
              (n, s) => n + 1 / (1 + (regional[s.region] ?? 0)),
              0,
            )) /
            primaries.length;
        if (preferences[e.id] === "FAVORITE") benefit *= 1.08;
        if (
          existing < 0 &&
          current.some((x) => x.exercise.pattern === e.pattern)
        )
          benefit *= 0.75;
        if (
          existing < 0 &&
          days.some((d) => d.some((x) => x.exercise.id === e.id))
        )
          benefit *= 0.93;
        const score =
          benefit /
          Math.sqrt(Math.max(1, duration - times[day])) /
          (1 + (5 * times[day]) / (p.sessionMinutes * 60));
        if (!best || score > best.score + 1e-12)
          best = { day, items: updated, score };
      }
    if (!best) break;
    days[best.day] = best.items;
  }
  return {
    id: "generated",
    title: `${focus.every((f) => f.groups.length === groups.length) ? "Full body" : "Сплит"} · ${p.daysPerWeek} дня`,
    createdAtEpochMillis: 0,
    days: days.map((items, i) => ({
      id: `day-${i + 1}`,
      name: `Тренировка ${String.fromCharCode(65 + i)}`,
      focus: focus[i].title,
      estimatedMinutes: Math.ceil(seconds(items) / 60),
      exercises: items
        .sort(
          (a, b) =>
            Number(!compound.includes(a.exercise.pattern)) -
              Number(!compound.includes(b.exercise.pattern)) ||
            p.priorityMuscles.reduce(
              (n, g) =>
                n +
                (coeff[b.exercise.id][g] ?? 0) -
                (coeff[a.exercise.id][g] ?? 0),
              0,
            ) ||
            (a.exercise.id < b.exercise.id ? -1 : 1),
        )
        .map((item) => {
          const primary = item.exercise.stimuli
            .filter((s) => s.role === "PRIMARY")
            .sort((a, b) => b.coefficient - a.coefficient)[0];
          const group = c.regions[primary.region].group;
          return {
            ...item,
            explanation: `${p.priorityMuscles.includes(group) ? "Дополнительный объём для приоритета" : "Дополняет недельную нагрузку"}: ${c.groups[group]}. Учтены время и подходы в других упражнениях.`,
          };
        }),
    })),
  };
}
export function alternatives(
  item: Prescription,
  day: Plan["days"][number],
  p: Profile,
  prefs: Record<string, string>,
  c: Catalog,
) {
  const original = coefficients(item.exercise, c),
    primary = Object.entries(original).sort(
      (a, b) => b[1]! - a[1]!,
    )[0]?.[0] as Group;
  return c.exercises
    .filter(
      (e) =>
        e.id !== item.exercise.id &&
        allowed(e, p, prefs) &&
        !day.exercises.some((x) => x.exercise.id === e.id) &&
        e.pattern === item.exercise.pattern &&
        (coefficients(e, c)[primary] ?? 0) >= 0.5 &&
        seconds(
          day.exercises.map((x) =>
            x === item
              ? { ...x, exercise: e, restSeconds: e.baseRestSeconds }
              : x,
          ),
        ) <=
          p.sessionMinutes * 60,
    )
    .sort((a, b) => {
      const ac = coefficients(a, c),
        bc = coefficients(b, c);
      return (
        groups.reduce(
          (n, g) =>
            n +
            Math.abs((original[g] ?? 0) - (ac[g] ?? 0)) -
            Math.abs((original[g] ?? 0) - (bc[g] ?? 0)),
          0,
        ) || a.id.localeCompare(b.id)
      );
    })
    .slice(0, 8);
}
