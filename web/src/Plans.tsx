import { useState } from "react";
import { ArrowRight, RefreshCw, SlidersHorizontal } from "lucide-react";
import {
  groups,
  profileSchema,
  seconds,
  type Profile,
  type Plan,
  type Exercise,
  type Data,
  type Catalog,
  type Media,
  type Prescription,
} from "./model";
import { assess, alternatives } from "./generator";
import { Coverage, ExerciseRow, Modal } from "./components";
export function Setup({
  profile,
  catalog,
  busy,
  onGenerate,
}: {
  profile: Profile;
  catalog: Catalog;
  busy: boolean;
  onGenerate: (p: Profile) => void;
}) {
  const [p, setP] = useState(profile),
    [error, setError] = useState("");
  const set = (key: keyof Profile, value: unknown) =>
    setP({ ...p, [key]: value });
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        const parsed = profileSchema.safeParse(p);
        if (!parsed.success || !p.equipment.length) {
          setError("Проверьте параметры и выберите оборудование.");
          return;
        }
        setError("");
        onGenerate(parsed.data);
      }}
    >
      <p className="eyebrow">ПРОГРАММА ПОД ВАС</p>
      <h1>
        Всё начинается
        <br />с вашего ритма.
      </h1>
      <p className="muted">
        Приоритеты влияют на недельный объём. Время — верхний предел, а не цель
        заполнить каждую минуту.
      </p>
      <section className="card">
        <h2>Расписание</h2>
        <div className="form-grid">
          <label>
            Дней в неделю
            <select
              value={p.daysPerWeek}
              onChange={(e) => set("daysPerWeek", Number(e.target.value))}
            >
              {[1, 2, 3, 4, 5, 6].map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
          <label>
            Минут на занятие
            <select
              value={p.sessionMinutes}
              onChange={(e) => set("sessionMinutes", Number(e.target.value))}
            >
              {[30, 45, 60, 90, 120].map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          Опыт
          <select
            value={p.experience}
            onChange={(e) => set("experience", e.target.value)}
          >
            <option value="BEGINNER">Новичок</option>
            <option value="INTERMEDIATE">Средний</option>
            <option value="ADVANCED">Продвинутый</option>
          </select>
        </label>
        <label>
          Формат
          <select
            value={p.splitPreference}
            onChange={(e) => set("splitPreference", e.target.value)}
          >
            <option value="AUTO">Подобрать автоматически</option>
            <option value="FULL_BODY">Full body — всё тело</option>
            <option value="SPLIT">Сплит — по группам</option>
          </select>
        </label>
        <label>
          Цель
          <select value={p.goal} onChange={(e) => set("goal", e.target.value)}>
            <option value="HYPERTROPHY">Рост мышц</option>
            <option value="MAINTENANCE">Поддержание</option>
          </select>
        </label>
      </section>
      <section className="card">
        <h2>Доступное оборудование</h2>
        <div className="choice-grid">
          {Object.entries(catalog.equipment).map(([key, title]) => (
            <label className="check-choice" key={key}>
              <input
                type="checkbox"
                checked={p.equipment.includes(key as never)}
                onChange={() =>
                  set(
                    "equipment",
                    p.equipment.includes(key as never)
                      ? p.equipment.filter((x) => x !== key)
                      : [...p.equipment, key],
                  )
                }
              />
              {title}
            </label>
          ))}
        </div>
      </section>
      <section className="card">
        <h2>Больше внимания</h2>
        <p className="muted small">
          Необязательно. Для выбранных мышц увеличится целевой объём.
        </p>
        <div className="choice-grid">
          {groups.map((g) => (
            <label className="check-choice" key={g}>
              <input
                type="checkbox"
                checked={p.priorityMuscles.includes(g)}
                onChange={() =>
                  set(
                    "priorityMuscles",
                    p.priorityMuscles.includes(g)
                      ? p.priorityMuscles.filter((x) => x !== g)
                      : [...p.priorityMuscles, g],
                  )
                }
              />
              {catalog.groups[g]}
            </label>
          ))}
        </div>
      </section>
      <section className="card">
        <h2>Параметры тела</h2>
        <div className="form-grid">
          <label>
            Рост, см
            <input
              type="number"
              required
              min="100"
              max="250"
              value={p.heightCm}
              onChange={(e) => set("heightCm", Number(e.target.value))}
            />
          </label>
          <label>
            Вес, кг
            <input
              type="number"
              required
              min="25"
              max="350"
              step="0.1"
              value={p.weightKg}
              onChange={(e) => set("weightKg", Number(e.target.value))}
            />
          </label>
        </div>
        <label>
          Боль или ограничения
          <textarea
            value={p.painNotes}
            onChange={(e) => set("painNotes", e.target.value)}
            maxLength={2000}
          />
        </label>
        <p className="muted small">
          Приложение не подбирает реабилитацию и не заменяет специалиста.
        </p>
      </section>
      {error && <p role="alert">{error}</p>}
      <button className="button full" disabled={busy}>
        {busy ? "Составляем программу…" : "Составить и сохранить"}
        <ArrowRight size={18} />
      </button>
    </form>
  );
}
export function PlanView({
  plan,
  data,
  catalog,
  media,
  onDetails,
  onStart,
  onSetup,
  onUpdate,
  busy,
}: {
  plan: Plan;
  data: Data;
  catalog: Catalog;
  media: Media;
  onDetails: (e: Exercise) => void;
  onStart: (i: number) => void;
  onSetup: () => void;
  onUpdate: (p: Plan) => void;
  busy: boolean;
}) {
  const [selected, setSelected] = useState(0),
    [replace, setReplace] = useState<Prescription | null>(null);
  const day = plan.days[Math.min(selected, plan.days.length - 1)],
    assessment = assess(plan, data.profile, catalog);
  return (
    <>
      <div className="between">
        <div>
          <p className="eyebrow">ВАША ПРОГРАММА</p>
          <h1>{plan.title}</h1>
        </div>
        <button
          className="icon"
          aria-label="Настроить программу"
          onClick={onSetup}
        >
          <SlidersHorizontal />
        </button>
      </div>
      <div className="chips days">
        {plan.days.map((d, i) => (
          <button
            key={d.id}
            className={`chip ${i === selected ? "selected" : ""}`}
            onClick={() => setSelected(i)}
          >
            {d.name}
          </button>
        ))}
      </div>
      <section className="card">
        <p className="eyebrow">{day.focus}</p>
        <h2>
          {day.estimatedMinutes} минут{" "}
          <span className="muted">
            / {day.exercises.reduce((n, x) => n + x.sets, 0)} подходов
          </span>
        </h2>
        <button
          className="button full"
          disabled={busy || !day.exercises.length}
          onClick={() => onStart(selected)}
        >
          {data.active ? "Продолжить тренировку" : "Начать тренировку"}
          <ArrowRight size={18} />
        </button>
      </section>
      <div className="exercise-list">
        {day.exercises.map((p, i) => (
          <article key={`${p.exercise.id}-${i}`}>
            <ExerciseRow
              exercise={p.exercise}
              media={media}
              onClick={() => onDetails(p.exercise)}
              subtitle={`${p.sets} × ${p.reps.first}–${p.reps.last} · отдых ${p.restSeconds} с · RIR ${p.rir}`}
            />
            <p className="muted small">{p.explanation}</p>
            <button className="subtle" onClick={() => setReplace(p)}>
              <RefreshCw size={14} /> Заменить упражнение
            </button>
          </article>
        ))}
      </div>
      <Coverage
        catalog={catalog}
        items={assessment.coverage}
        title="Покрытие за неделю"
        description="Условные эффективные подходы с косвенной нагрузкой. Справа — целевой диапазон, а не количество выполненных подходов."
      />
      {!!assessment.warnings.length && (
        <details className="card warnings">
          <summary>
            На что обратить внимание · {assessment.warnings.length}
          </summary>
          {assessment.warnings.map((w, i) => (
            <p key={i}>{w}</p>
          ))}
        </details>
      )}
      <button className="button secondary full" onClick={onSetup}>
        Составить другую программу
      </button>
      {replace && (
        <Modal title="Заменить упражнение" onClose={() => setReplace(null)}>
          {alternatives(
            replace,
            day,
            data.profile,
            data.preferences,
            catalog,
          ).map((e) => (
            <ExerciseRow
              key={e.id}
              exercise={e}
              media={media}
              subtitle="Сохранить замену и пересчитать покрытие"
              onClick={() => {
                const days = plan.days
                  .map((d) =>
                    d.id !== day.id
                      ? d
                      : {
                          ...d,
                          exercises: d.exercises.map((x) =>
                            x === replace
                              ? {
                                  ...x,
                                  exercise: e,
                                  reps: e.repRange,
                                  restSeconds: e.baseRestSeconds,
                                  explanation:
                                    "Выбранная замена с сохранением паттерна движения.",
                                }
                              : x,
                          ),
                        },
                  )
                  .map((d) => ({
                    ...d,
                    estimatedMinutes: Math.ceil(seconds(d.exercises) / 60),
                  }));
                onUpdate({ ...plan, days });
                setReplace(null);
              }}
            />
          ))}
          {!alternatives(replace, day, data.profile, data.preferences, catalog)
            .length && (
            <p>
              Нет подходящих замен с выбранным оборудованием и лимитом времени.
            </p>
          )}
        </Modal>
      )}
    </>
  );
}
