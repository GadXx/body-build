import { useEffect, useState } from "react";
import { Plus, Minus, Check, SkipForward, Timer } from "lucide-react";
import {
  nextSet,
  previousSet,
  recordSet,
  skipExercise,
  addExercise,
  coefficients,
  groups,
  type Catalog,
  type Data,
  type Exercise,
  type Media,
  type Session,
  type Target,
} from "./model";
import { Photos, CatalogView, Modal, fmt, clock } from "./components";
type Props = {
  data: Data;
  catalog: Catalog;
  media: Media;
  update: (fn: (d: Data) => Data) => Promise<boolean>;
  onDetails: (e: Exercise) => void;
  onDone: () => void;
  busy: boolean;
};
export function TrainingView({
  data,
  catalog,
  media,
  update,
  onDetails,
  onDone,
  busy,
}: Props) {
  const [now, setNow] = useState(Date.now()),
    [picker, setPicker] = useState(false);
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 500);
    const refresh = () => setNow(Date.now());
    document.addEventListener("visibilitychange", refresh);
    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, []);
  const s = data.active;
  if (!s)
    return (
      <div className="empty">
        <h2>Нет активной тренировки</h2>
        <button className="button" onClick={onDone}>
          На главную
        </button>
      </div>
    );
  const next = nextSet(s),
    resting = (s.restEndsAtEpochMillis ?? 0) > now;
  const apply = async (fn: (s: Session) => Session) => {
    let done = false;
    const ok = await update((d) => {
      if (!d.active) return d;
      const updated = fn(d.active);
      done = updated.completedAtEpochMillis !== null;
      return {
        ...d,
        active: done ? null : updated,
        history: done ? [updated, ...d.history] : d.history,
      };
    });
    if (ok && done) onDone();
  };
  const worked = new Set(
    s.completedSets.flatMap((set) =>
      Object.keys(
        coefficients(s.day.exercises[set.exerciseIndex].exercise, catalog),
      ),
    ),
  );
  const current = next
    ? Object.keys(coefficients(next.prescription.exercise, catalog))
    : [];
  const total = s.day.exercises.reduce(
    (n, p, i) =>
      n +
      (s.skippedExerciseIndices.includes(i)
        ? s.completedSets.filter((x) => x.exerciseIndex === i).length
        : p.sets),
    0,
  );
  return (
    <>
      <div className="session-heading">
        <div>
          <p className="eyebrow">
            {s.mode === "FREE" ? "СВОБОДНЫЙ РЕЖИМ" : "ПО ПРОГРАММЕ"}
          </p>
          <h1>{s.day.name}</h1>
        </div>
        <div className="timer-total">
          <Timer size={16} />
          {clock((now - s.startedAtEpochMillis) / 1000)}
        </div>
      </div>
      <div className="card compact">
        <div className="between">
          <strong>Ваше занятие</strong>
          <span>
            {s.completedSets.length}
            {s.mode === "PLANNED" ? ` / ${total}` : ""} подходов
          </span>
        </div>
        {s.mode === "PLANNED" && (
          <progress
            aria-label="Прогресс тренировки"
            max={Math.max(1, total)}
            value={s.completedSets.length}
          />
        )}
        <div className="chips">
          {groups
            .filter((g) => worked.has(g) || current.includes(g))
            .map((g) => (
              <span
                className={`chip ${current.includes(g) ? "selected" : ""}`}
                key={g}
              >
                {catalog.groups[g]} ·{" "}
                {current.includes(g) ? "сейчас" : "выполнено"}
              </span>
            ))}
        </div>
        {s.mode === "PLANNED" && (
          <p className="muted small">
            Осталось:{" "}
            {[
              ...new Set(
                s.day.exercises
                  .filter(
                    (_, i) =>
                      !s.skippedExerciseIndices.includes(i) &&
                      i >= (next?.exerciseIndex ?? s.day.exercises.length),
                  )
                  .map((x) => x.exercise.name),
              ),
            ].join(" · ") || "—"}
          </p>
        )}
      </div>
      {resting && (
        <section className="rest-card">
          <p className="eyebrow">ВРЕМЯ ВОССТАНОВИТЬСЯ</p>
          <div className="rest-time" role="timer">
            {clock(Math.ceil((s.restEndsAtEpochMillis! - now) / 1000))}
          </div>
          <p>
            {next
              ? `Дальше: ${next.prescription.exercise.name}`
              : "Выберите следующий подход, когда будете готовы."}
          </p>
          <div className="row">
            <button
              className="button secondary"
              disabled={busy}
              onClick={() =>
                apply((x) => ({
                  ...x,
                  restEndsAtEpochMillis:
                    Math.max(Date.now(), x.restEndsAtEpochMillis ?? 0) + 30000,
                }))
              }
            >
              +30 секунд
            </button>
            <button
              className="button"
              disabled={busy}
              onClick={() =>
                apply((x) => ({ ...x, restEndsAtEpochMillis: null }))
              }
            >
              Пропустить отдых
            </button>
          </div>
          <small>
            При блокировке экрана таймер продолжит отсчёт. Фоновый звуковой
            сигнал на iOS не гарантируется.
          </small>
        </section>
      )}
      {!resting && next && (
        <SetForm
          key={`${s.id}-${next.exerciseIndex}-${next.setNumber}`}
          target={next}
          session={s}
          data={data}
          media={media}
          busy={busy}
          onRecord={(reps, weight) =>
            apply((x) =>
              recordSet(x, next, reps, weight, data.profile.weightKg),
            )
          }
          onSkip={() =>
            confirm(
              "Пропустить оставшиеся подходы упражнения? Выполненные останутся в истории.",
            ) && apply((x) => skipExercise(x))
          }
        />
      )}
      {s.mode === "FREE" && !next && (
        <section className="card">
          <p className="eyebrow">ВАШ РИТМ, ВАШ ВЫБОР</p>
          <h2>
            {s.completedSets.length ? "Что сделаем дальше?" : "С чего начнём?"}
          </h2>
          <p className="muted">
            Добавляйте по одному подходу. Завершение — только по вашей кнопке.
          </p>
          {s.day.exercises.length > 0 &&
            !s.skippedExerciseIndices.includes(s.day.exercises.length - 1) && (
              <button
                className="button secondary full"
                disabled={busy}
                onClick={() =>
                  apply((x) => addExercise(x, x.day.exercises.at(-1)!.exercise))
                }
              >
                <Plus size={18} /> Ещё подход ·{" "}
                {s.day.exercises.at(-1)!.exercise.name}
              </button>
            )}
          <button
            className="button full"
            disabled={busy}
            onClick={() => setPicker(true)}
          >
            <Plus size={20} /> Выбрать упражнение
          </button>
        </section>
      )}
      {!!s.completedSets.length && (
        <section className="card">
          <h2>Уже записано</h2>
          {[...new Set(s.completedSets.map((x) => x.exerciseId))].map((id) => (
            <div className="between summary-line" key={id}>
              <span>
                {s.completedSets.find((x) => x.exerciseId === id)!.exerciseName}
              </span>
              <strong>
                {s.completedSets.filter((x) => x.exerciseId === id).length}{" "}
                подх.
              </strong>
            </div>
          ))}
        </section>
      )}
      {s.mode === "FREE" && (
        <button
          disabled={busy || !s.completedSets.length}
          className="button full"
          onClick={() =>
            confirm(
              `Завершить тренировку? Сохранится ${s.completedSets.length} выполненных подходов.`,
            ) &&
            apply((x) => ({
              ...x,
              completedAtEpochMillis: Date.now(),
              restEndsAtEpochMillis: null,
            }))
          }
        >
          <Check size={19} /> Завершить и сохранить
        </button>
      )}
      <button
        className="subtle danger full"
        disabled={busy}
        onClick={async () => {
          if (confirm("Отменить занятие и удалить все его записи?"))
            if (await update((d) => ({ ...d, active: null }))) onDone();
        }}
      >
        Отменить тренировку
      </button>
      {picker && (
        <Modal title="Выбрать упражнение" onClose={() => setPicker(false)}>
          <CatalogView
            catalog={catalog}
            media={media}
            onDetails={onDetails}
            preferences={data.preferences}
            onPick={async (e) => {
              await apply((x) => addExercise(x, e));
              setPicker(false);
            }}
          />
        </Modal>
      )}
    </>
  );
}
function SetForm({
  target,
  session,
  data,
  media,
  busy,
  onRecord,
  onSkip,
}: {
  target: Target;
  session: Session;
  data: Data;
  media: Media;
  busy: boolean;
  onRecord: (reps: number, weight: number) => void;
  onSkip: () => void;
}) {
  const e = target.prescription.exercise,
    previous = previousSet(data.history, session, target),
    fallback =
      previous ??
      session.completedSets.filter((x) => x.exerciseId === e.id).at(-1);
  const [reps, setReps] = useState(
      String(fallback?.repetitions ?? target.prescription.reps.first),
    ),
    [weight, setWeight] = useState(
      fallback && !fallback.bodyweight ? String(fallback.weightKg) : "",
    );
  const bodyweight = e.equipment.includes("BODYWEIGHT");
  const numericWeight = Number(weight.replace(",", "."));
  const valid =
    Number.isInteger(Number(reps)) &&
    Number(reps) > 0 &&
    Number(reps) <= 999 &&
    (bodyweight ||
      (weight.trim() !== "" &&
        Number.isFinite(numericWeight) &&
        numericWeight >= 0 &&
        numericWeight <= 10000));
  const ordinal =
    session.completedSets.filter((x) => x.exerciseId === e.id).length + 1;
  return (
    <section className="card current-exercise">
      <p className="eyebrow">
        ПОДХОД {ordinal}
        {session.mode === "PLANNED" ? ` / ${target.prescription.sets}` : ""}
      </p>
      <h2>{e.name}</h2>
      <Photos exercise={e} media={media} />
      {previous && (
        <p className="previous">
          Прошлый раз · подход {ordinal}:{" "}
          <b>
            {previous.repetitions} ×{" "}
            {previous.bodyweight
              ? "вес тела"
              : `${fmt(previous.weightKg, 1)} кг`}
          </b>
        </p>
      )}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (valid)
            onRecord(
              Number(reps),
              bodyweight ? 0 : Number(weight.replace(",", ".")),
            );
        }}
      >
        <label>
          Повторения
          <div className="stepper">
            <button
              type="button"
              className="icon"
              aria-label="Уменьшить повторения"
              onClick={() =>
                setReps(String(Math.max(1, (Number(reps) || 1) - 1)))
              }
            >
              <Minus />
            </button>
            <input
              aria-label="Повторения"
              inputMode="numeric"
              pattern="[0-9]*"
              value={reps}
              onChange={(event) =>
                setReps(event.target.value.replace(/\D/g, "").slice(0, 3))
              }
            />
            <button
              type="button"
              className="icon"
              aria-label="Увеличить повторения"
              onClick={() =>
                setReps(String(Math.min(999, (Number(reps) || 0) + 1)))
              }
            >
              <Plus />
            </button>
          </div>
        </label>
        {bodyweight ? (
          <p className="muted small">
            Вес тела: {fmt(data.profile.weightKg, 1)} кг из профиля.
          </p>
        ) : (
          <label>
            Вес, кг
            <input
              inputMode="decimal"
              placeholder="Например, 20"
              value={weight}
              onChange={(event) => setWeight(event.target.value)}
              required
            />
          </label>
        )}
        <button className="button full" disabled={busy || !valid}>
          <Check size={20} />
          {busy ? "Сохраняем…" : "Подход выполнен"}
        </button>
      </form>
      <button className="subtle full" disabled={busy} onClick={onSkip}>
        <SkipForward size={17} /> Пропустить упражнение
      </button>
    </section>
  );
}
