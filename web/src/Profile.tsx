import { useState, type ChangeEvent } from "react";
import {
  Download,
  Upload,
  ChevronDown,
  Trash2,
  TrendingUp,
} from "lucide-react";
import {
  actualLoad,
  groups,
  index,
  effectiveWeight,
  tonnage,
  dataSchema,
  type Data,
  type Catalog,
  type Session,
  type CompletedSet,
} from "./model";
import { Coverage, Modal, fmt, date, clock } from "./components";
type Props = {
  data: Data;
  catalog: Catalog;
  update: (fn: (d: Data) => Data) => Promise<boolean>;
  openPlan: (id: string) => void;
  busy: boolean;
  error: (s: string) => void;
};
export function ProfileView({
  data,
  catalog,
  update,
  openPlan,
  busy,
  error,
}: Props) {
  const [tab, setTab] = useState("history"),
    [selected, setSelected] = useState<string[]>([]),
    [analysis, setAnalysis] = useState(false),
    [expanded, setExpanded] = useState(""),
    [edit, setEdit] = useState(false),
    [height, setHeight] = useState(String(data.profile.heightCm)),
    [weight, setWeight] = useState(String(data.profile.weightKg));
  const history = [...data.history].sort(
      (a, b) => b.startedAtEpochMillis - a.startedAtEpochMillis,
    ),
    chosen = history.filter((s) => selected.includes(s.id));
  const [period, setPeriod] = useState("last"),
    [graph, setGraph] = useState<string | null>(null);
  const exercises = [
    ...new Map(
      history
        .flatMap((s) => s.completedSets)
        .map((s) => [s.exerciseId, s.exerciseName]),
    ).entries(),
  ];
  const series = (id: string) => {
    const all = history
      .map((s) => {
        const sets = s.completedSets
          .filter((x) => x.exerciseId === id)
          .sort((a, b) => index(b) - index(a));
        return sets[0] ? { time: s.startedAtEpochMillis, set: sets[0] } : null;
      })
      .filter((x) => x !== null)
      .sort((a, b) => a.time - b.time);
    if (period === "last") return all.slice(-2);
    const cutoff = Date.now() - (period === "week" ? 7 : 30) * 86400000,
      inside = all.filter((x) => x.time >= cutoff),
      baseline = all.filter((x) => x.time < cutoff).at(-1);
    return inside.length ? [...(baseline ? [baseline] : []), ...inside] : [];
  };
  const delta = (points: { set: CompletedSet }[]) =>
    points.length < 2 || index(points[0].set) <= 0
      ? null
      : (index(points.at(-1)!.set) / index(points[0].set) - 1) * 100;
  async function backup() {
    const file = new File(
      [JSON.stringify(data, null, 2)],
      `body-build-${new Date().toISOString().slice(0, 10)}.json`,
      { type: "application/json" },
    );
    if (navigator.canShare?.({ files: [file] })) {
      try {
        await navigator.share({
          files: [file],
          title: "Резервная копия Body Build",
        });
        return;
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") return;
      }
    }
    const url = URL.createObjectURL(file),
      a = document.createElement("a");
    a.href = url;
    a.download = file.name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }
  async function restore(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    try {
      if (file.size > 30 * 1024 * 1024)
        throw Error("Файл слишком большой (максимум 30 МБ).");
      const imported = dataSchema.parse(JSON.parse(await file.text()));
      if (data.active)
        throw Error("Сначала завершите или отмените текущую тренировку.");
      if (
        !confirm(
          "Заменить все записи веб-версии этой резервной копией? Сначала сохраните текущую копию.",
        )
      )
        return;
      await update(() => imported);
      setSelected([]);
    } catch (e) {
      error(
        e instanceof Error && e.message.includes("Сначала")
          ? e.message
          : "Не удалось восстановить копию: неверный или повреждённый файл.",
      );
    }
  }
  return (
    <>
      <div className="profile-card">
        <div>
          <p className="eyebrow">ВАШИ ПАРАМЕТРЫ</p>
          <h2>
            {data.profile.heightCm} <small>см</small> ·{" "}
            {fmt(data.profile.weightKg, 1)} <small>кг</small>
          </h2>
        </div>
        <button
          className="subtle"
          onClick={() => {
            setHeight(String(data.profile.heightCm));
            setWeight(String(data.profile.weightKg));
            setEdit(true);
          }}
        >
          Изменить
        </button>
      </div>
      <div className="stats">
        <div>
          <b>{history.length}</b>
          <small>тренировок</small>
        </div>
        <div>
          <b>{history.reduce((n, s) => n + s.completedSets.length, 0)}</b>
          <small>подходов</small>
        </div>
        <div>
          <b>
            {fmt(
              history
                .flatMap((s) => s.completedSets)
                .reduce((n, s) => n + tonnage(s), 0) / 1000,
              1,
            )}
          </b>
          <small>тонн суммарно</small>
        </div>
      </div>
      <div className="segmented">
        {[
          ["history", "История"],
          ["progress", "Прогресс"],
          ["plans", "Программы"],
        ].map(([id, title]) => (
          <button key={id} aria-pressed={tab === id} onClick={() => setTab(id)}>
            {title}
          </button>
        ))}
      </div>
      {tab === "history" && (
        <>
          <p className="muted">
            Отметьте занятия, чтобы посмотреть нагрузку на мышцы за выбранные
            тренировки.
          </p>
          {!!history.length && (
            <button
              className="subtle"
              onClick={() =>
                setSelected(
                  chosen.length === history.length
                    ? []
                    : history.map((s) => s.id),
                )
              }
            >
              {chosen.length === history.length ? "Снять все" : "Выбрать все"}
            </button>
          )}
          {!history.length && (
            <div className="empty">
              <TrendingUp />
              <h2>Здесь начинается ваша история</h2>
              <p>Завершите первое занятие. По плану или в своём ритме.</p>
            </div>
          )}
          {history.map((s) => (
            <article
              className={`card history-card ${selected.includes(s.id) ? "checked" : ""}`}
              key={s.id}
            >
              <label className="history-title">
                <input
                  type="checkbox"
                  checked={selected.includes(s.id)}
                  onChange={() =>
                    setSelected(
                      selected.includes(s.id)
                        ? selected.filter((x) => x !== s.id)
                        : [...selected, s.id],
                    )
                  }
                />
                <span>
                  <small>
                    {date(s.startedAtEpochMillis)} ·{" "}
                    {s.mode === "FREE" ? "Свободная" : "По плану"}
                  </small>
                  <strong>{s.day.name}</strong>
                </span>
              </label>
              <p>
                {s.completedSets.length} подходов ·{" "}
                {clock(
                  (s.completedAtEpochMillis! - s.startedAtEpochMillis) / 1000,
                )}{" "}
                · {fmt(s.completedSets.reduce((n, s) => n + tonnage(s), 0))} кг
              </p>
              <p className="muted small clamp">
                {[...new Set(s.completedSets.map((x) => x.exerciseName))].join(
                  " · ",
                )}
              </p>
              <button
                className="subtle"
                onClick={() => setExpanded(expanded === s.id ? "" : s.id)}
              >
                Подходы <ChevronDown size={16} />
              </button>
              {expanded === s.id && (
                <>
                  <SetList session={s} />
                  <button
                    disabled={busy}
                    className="danger subtle"
                    onClick={() =>
                      confirm("Удалить тренировку из истории и статистики?") &&
                      update((d) => ({
                        ...d,
                        history: d.history.filter((x) => x.id !== s.id),
                      }))
                    }
                  >
                    <Trash2 size={16} /> Удалить тренировку
                  </button>
                </>
              )}
            </article>
          ))}
          {!!chosen.length && (
            <div className="selection-bar">
              <button className="subtle" onClick={() => setSelected([])}>
                Снять
              </button>
              <button className="button" onClick={() => setAnalysis(true)}>
                Анализ нагрузки · {chosen.length}
              </button>
            </div>
          )}
        </>
      )}
      {tab === "plans" && (
        <>
          {!data.plans.length && (
            <div className="empty">Сохранённых программ пока нет.</div>
          )}
          {data.plans.map((plan) => (
            <article className="card" key={plan.id}>
              <p className="eyebrow">{date(plan.createdAtEpochMillis)}</p>
              <h2>{plan.title}</h2>
              <p>{plan.days.length} тренировок</p>
              <div className="between">
                <button
                  className="button secondary"
                  onClick={() => openPlan(plan.id)}
                >
                  Открыть программу
                </button>
                <button
                  className="icon danger"
                  aria-label="Удалить программу"
                  disabled={busy}
                  onClick={() =>
                    confirm(
                      "Удалить программу? История выполнений останется.",
                    ) &&
                    update((d) => ({
                      ...d,
                      plans: d.plans.filter((x) => x.id !== plan.id),
                      activePlanId:
                        d.activePlanId === plan.id ? null : d.activePlanId,
                    }))
                  }
                >
                  <Trash2 size={20} />
                </button>
              </div>
            </article>
          ))}
        </>
      )}
      {tab === "progress" && (
        <>
          <p className="muted small">
            Условный силовой индекс: вес × (1 + повторения / 30), лучший подход
            занятия. Вес тела учитывается автоматически. Это ориентир, не
            измерение силы.
          </p>
          <div className="chips">
            {[
              ["last", "Последний раз"],
              ["week", "Неделя"],
              ["month", "Месяц"],
            ].map(([id, title]) => (
              <button
                className={`chip ${period === id ? "selected" : ""}`}
                key={id}
                onClick={() => setPeriod(id)}
              >
                {title}
              </button>
            ))}
          </div>
          {exercises.map(([id, name]) => {
            const points = series(id),
              change = delta(points);
            return points.length ? (
              <button
                className="progress-row"
                key={id}
                onClick={() => setGraph(id)}
              >
                <span>
                  <strong>{name}</strong>
                  <small>Открыть график ↗</small>
                </span>
                <span>
                  <b>{fmt(index(points.at(-1)!.set), 1)}</b>
                  <small
                    className={
                      change !== null && change < 0 ? "negative" : "positive"
                    }
                  >
                    {change === null
                      ? "Нет сравнения"
                      : `${change >= 0 ? "+" : ""}${fmt(change, 1)}%`}
                  </small>
                </span>
              </button>
            ) : null;
          })}
          {!exercises.some(([id]) => series(id).length > 0) && (
            <div className="empty">Нет результатов за выбранный период.</div>
          )}
        </>
      )}
      <section className="card backup">
        <h2>Записи под вашим контролем</h2>
        <p className="muted small">
          Данные хранятся только в этом браузере. Очистка данных сайта удалит
          историю. Регулярно сохраняйте копию. Android и PWA пока не
          синхронизируются.
        </p>
        <div className="row">
          <button className="subtle" onClick={backup}>
            <Download size={18} /> Сохранить копию
          </button>
          <label className="file-button">
            <Upload size={18} /> Восстановить
            <input
              type="file"
              accept="application/json,.json"
              disabled={busy}
              onChange={restore}
            />
          </label>
        </div>
      </section>
      {analysis && (
        <Modal title="Анализ нагрузки" onClose={() => setAnalysis(false)}>
          <p>
            {chosen.length} выбранных тренировок
            {chosen.length
              ? ` · ${date(Math.min(...chosen.map((s) => s.startedAtEpochMillis)))} — ${date(Math.max(...chosen.map((s) => s.startedAtEpochMillis)))}`
              : ""}
          </p>
          <Coverage
            catalog={catalog}
            title="Мышечное покрытие"
            description="Выполненные подходы × участие мышцы. Пропуски не учитываются. Это распределение нагрузки, а не выполнение недельной нормы; RIR не измерен."
            items={Object.entries(actualLoad(chosen, catalog)).map(
              ([group, value]) => ({
                group: group as (typeof groups)[number],
                value,
              }),
            )}
          />
        </Modal>
      )}
      {edit && (
        <Modal title="Рост и вес" onClose={() => setEdit(false)}>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              if (
                await update((d) => ({
                  ...d,
                  profile: {
                    ...d.profile,
                    heightCm: Number(height),
                    weightKg: Number(weight.replace(",", ".")),
                  },
                }))
              )
                setEdit(false);
            }}
          >
            <label>
              Рост, см
              <input
                type="number"
                min="100"
                max="250"
                required
                value={height}
                onChange={(e) => setHeight(e.target.value)}
              />
            </label>
            <label>
              Вес, кг
              <input
                type="text"
                inputMode="decimal"
                required
                value={weight}
                onChange={(e) => setWeight(e.target.value)}
              />
            </label>
            <p className="muted">
              Новый вес применяется к будущим подходам. Старые записи не
              изменятся.
            </p>
            <button
              className="button"
              disabled={
                busy ||
                !Number.isFinite(Number(weight.replace(",", "."))) ||
                Number(weight.replace(",", ".")) < 25 ||
                Number(weight.replace(",", ".")) > 350
              }
            >
              Сохранить параметры
            </button>
          </form>
        </Modal>
      )}
      {graph && (
        <Modal
          title={exercises.find(([id]) => id === graph)?.[1] ?? "Прогресс"}
          onClose={() => setGraph(null)}
        >
          <ProgressChart points={series(graph)} />
          <p className="muted small">
            Для недели и месяца сравнение начинается с последнего результата до
            выбранного периода, если он есть.
          </p>
        </Modal>
      )}
    </>
  );
}
function SetList({ session }: { session: Session }) {
  return (
    <div className="set-list">
      {session.completedSets.map((s, i) => (
        <div key={i}>
          <span>{s.exerciseName}</span>
          <strong>
            {s.repetitions} × {fmt(effectiveWeight(s), 1)} кг
          </strong>
        </div>
      ))}
    </div>
  );
}
function ProgressChart({
  points,
}: {
  points: { time: number; set: CompletedSet }[];
}) {
  const [selected, setSelected] = useState(points.length - 1),
    scores = points.map((x) => index(x.set)),
    low = Math.min(...scores) * 0.9,
    high = Math.max(low + 1, ...scores) * 1.05;
  const coords = points.map((x, i) => ({
    x: 30 + (points.length === 1 ? 150 : (i / (points.length - 1)) * 300),
    y: 180 - ((index(x.set) - low) / (high - low)) * 150,
  }));
  return (
    <>
      <div className="graph">
        <svg
          viewBox="0 0 360 210"
          role="img"
          aria-label="График силового индекса"
        >
          <defs>
            <linearGradient id="fade" x1="0" y1="0" x2="0" y2="1">
              <stop stopColor="#d3fa75" stopOpacity=".25" />
              <stop offset="1" stopColor="#d3fa75" stopOpacity="0" />
            </linearGradient>
          </defs>
          {[30, 80, 130, 180].map((y) => (
            <line key={y} x1="20" x2="340" y1={y} y2={y} stroke="#36423c" />
          ))}
          {coords.length > 1 && (
            <path
              d={`M ${coords[0].x},190 L ${coords.map((p) => `${p.x},${p.y}`).join(" L ")} L ${coords.at(-1)!.x},190 Z`}
              fill="url(#fade)"
            />
          )}
          <polyline
            points={coords.map((p) => `${p.x},${p.y}`).join(" ")}
            fill="none"
            stroke="#d3fa75"
            strokeWidth="3"
          />
          {coords.map((p, i) => (
            <circle
              key={i}
              cx={p.x}
              cy={p.y}
              r={i === selected ? 7 : 4}
              fill="#d3fa75"
            />
          ))}
        </svg>
      </div>
      <div className="chips">
        {points.map((p, i) => (
          <button
            key={i}
            className={`chip ${i === selected ? "selected" : ""}`}
            onClick={() => setSelected(i)}
          >
            {date(p.time)}
          </button>
        ))}
      </div>
      {points[selected] && (
        <div className="card">
          <p className="eyebrow">{date(points[selected].time)}</p>
          <h2>Индекс {fmt(index(points[selected].set), 1)}</h2>
          <p>
            {points[selected].set.repetitions} повторений ×{" "}
            {fmt(effectiveWeight(points[selected].set), 1)} кг
          </p>
        </div>
      )}
    </>
  );
}
