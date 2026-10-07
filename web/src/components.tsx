import { useEffect, useRef, useState, type ReactNode } from "react";
import { X, ArrowUpRight, Search, ChevronRight } from "lucide-react";
import {
  groups,
  matches,
  coefficients,
  type Catalog,
  type Exercise,
  type Group,
  type Media,
} from "./model";
export const fmt = (n: number, digits = 0) =>
  n.toLocaleString("ru-RU", { maximumFractionDigits: digits });
export const date = (n: number) =>
  new Date(n).toLocaleDateString("ru-RU", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
export const clock = (n: number) => {
  const seconds = Math.max(0, Math.floor(n));
  return `${Math.floor(seconds / 3600) ? `${Math.floor(seconds / 3600)}:` : ""}${String(Math.floor(seconds / 60) % 60).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
};
export const photoUrl = (e: Exercise, m: Media, frame = 0) =>
  `./exercise-photos/${m.sources[e.id]}/${frame}.jpg`;
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
    const old = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = old;
    };
  }, []);
  return (
    <dialog ref={ref} onCancel={onClose}>
      <header className="modal-head">
        <h2>{title}</h2>
        <button className="icon" aria-label="Закрыть" onClick={onClose}>
          <X />
        </button>
      </header>
      <div className="modal-body">{children}</div>
    </dialog>
  );
}
export function Photos({
  exercise,
  media,
}: {
  exercise: Exercise;
  media: Media;
}) {
  const [frame, setFrame] = useState(
    ["plank", "side-plank"].includes(exercise.id) ? 1 : 0,
  );
  return (
    <div>
      <img
        className="exercise-photo"
        src={photoUrl(exercise, media, frame)}
        alt={`${exercise.name}, позиция ${frame + 1}`}
      />
      <div className="segmented">
        {[0, 1].map((i) => (
          <button
            aria-pressed={frame === i}
            key={i}
            onClick={() => setFrame(i)}
          >
            Позиция {i + 1}
          </button>
        ))}
      </div>
      {media.notes[exercise.id] && (
        <p className="notice">{media.notes[exercise.id]}</p>
      )}
    </div>
  );
}
export function ExerciseDetails({
  exercise,
  media,
  catalog,
  onClose,
}: {
  exercise: Exercise;
  media: Media;
  catalog: Catalog;
  onClose: () => void;
}) {
  return (
    <Modal title={exercise.name} onClose={onClose}>
      <Photos key={exercise.id} exercise={exercise} media={media} />
      <p>{exercise.equipment.map((x) => catalog.equipment[x]).join(" · ")}</p>
      <div className="chips">
        {Object.keys(coefficients(exercise, catalog)).map((g) => (
          <span className="chip" key={g}>
            {catalog.groups[g as Group]}
          </span>
        ))}
      </div>
      {exercise.warnings.map((w) => (
        <p className="notice" key={w}>
          {w}
        </p>
      ))}
      <p className="muted small">
        Фото:{" "}
        <a
          href="https://github.com/yuhonas/free-exercise-db"
          target="_blank"
          rel="noreferrer"
        >
          Free Exercise DB
        </a>{" "}
        · Unlicense. Коэффициенты нагрузки — условная модель, не медицинское
        измерение.
      </p>
    </Modal>
  );
}
export function ExerciseRow({
  exercise,
  media,
  subtitle,
  onClick,
}: {
  exercise: Exercise;
  media: Media;
  subtitle?: string;
  onClick: () => void;
}) {
  return (
    <button className="exercise-row" onClick={onClick}>
      <img loading="lazy" src={photoUrl(exercise, media)} alt="" />
      <span>
        <strong>{exercise.name}</strong>
        <small>{subtitle}</small>
      </span>
      <ChevronRight size={18} />
    </button>
  );
}
export function CatalogView({
  catalog,
  media,
  onDetails,
  onPick,
  preferences,
  onPreference,
}: {
  catalog: Catalog;
  media: Media;
  onDetails: (e: Exercise) => void;
  onPick?: (e: Exercise) => void;
  preferences: Record<string, string>;
  onPreference?: (id: string, kind: "FAVORITE" | "EXCLUDED" | null) => void;
}) {
  const [q, setQ] = useState(""),
    [group, setGroup] = useState(""),
    [equipment, setEquipment] = useState("");
  const filtered = catalog.exercises
    .filter(
      (e) =>
        matches(e, q, catalog) &&
        (!group ||
          e.stimuli.some(
            (s) =>
              s.role === "PRIMARY" && catalog.regions[s.region].group === group,
          )) &&
        (!equipment || e.equipment.includes(equipment as never)),
    )
    .sort((a, b) => a.name.localeCompare(b.name, "ru"));
  return (
    <>
      <label className="search">
        <Search size={20} />
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Упражнение, мышца или гриф"
          aria-label="Поиск упражнений"
        />
      </label>
      <div className="form-grid">
        <label>
          Мышцы
          <select value={group} onChange={(e) => setGroup(e.target.value)}>
            <option value="">Все группы</option>
            {groups.map((g) => (
              <option key={g} value={g}>
                {catalog.groups[g]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Оборудование
          <select
            value={equipment}
            onChange={(e) => setEquipment(e.target.value)}
          >
            <option value="">Любое</option>
            {Object.entries(catalog.equipment).map(([id, name]) => (
              <option key={id} value={id}>
                {name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="section-heading">
        <span>{filtered.length} упражнений</span>
        <small>
          Фотографии техники <ArrowUpRight size={13} />
        </small>
      </div>
      <div className="exercise-list">
        {filtered.map((e) => (
          <article key={e.id}>
            <ExerciseRow
              exercise={e}
              media={media}
              subtitle={e.equipment
                .map((x) => catalog.equipment[x])
                .join(" · ")}
              onClick={() => onDetails(e)}
            />
            {onPick ? (
              <button className="button small-button" onClick={() => onPick(e)}>
                Добавить подход
              </button>
            ) : (
              <div className="row">
                <button
                  className={`chip ${preferences[e.id] === "FAVORITE" ? "selected" : ""}`}
                  onClick={() =>
                    onPreference?.(
                      e.id,
                      preferences[e.id] === "FAVORITE" ? null : "FAVORITE",
                    )
                  }
                >
                  ♡ Любимое
                </button>
                <button
                  className={`chip ${preferences[e.id] === "EXCLUDED" ? "selected" : ""}`}
                  onClick={() =>
                    onPreference?.(
                      e.id,
                      preferences[e.id] === "EXCLUDED" ? null : "EXCLUDED",
                    )
                  }
                >
                  Исключить из плана
                </button>
              </div>
            )}
          </article>
        ))}
      </div>
      {!filtered.length && (
        <div className="empty">
          Ничего не найдено. Измените поиск или фильтры.
        </div>
      )}
    </>
  );
}
export function Coverage({
  catalog,
  items,
  title,
  description,
}: {
  catalog: Catalog;
  items: { group: Group; value: number; min?: number; max?: number }[];
  title: string;
  description: string;
}) {
  const max = Math.max(1, ...items.map((x) => x.value));
  return (
    <section className="card">
      <h2>{title}</h2>
      <p className="muted small">{description}</p>
      <div className="coverage">
        {items.map((x) => (
          <div key={x.group}>
            <div className="between">
              <span>{catalog.groups[x.group]}</span>
              <strong>
                {fmt(x.value, 1)}
                {x.max !== undefined && (
                  <small>
                    {" "}
                    / {fmt(x.min!, 1)}–{fmt(x.max, 1)}
                  </small>
                )}
              </strong>
            </div>
            <div className="bar">
              <i
                style={{
                  width: `${Math.min(100, (x.value / (x.max ?? max)) * 100)}%`,
                }}
                className={
                  x.min !== undefined &&
                  (x.value + 0.25 < x.min || x.value > x.max! + 0.05)
                    ? "low"
                    : ""
                }
              />
            </div>
            {!x.value && (
              <small className="muted">Нет записанной нагрузки</small>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
