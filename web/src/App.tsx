import { useEffect, useRef, useState } from "react";
import {
  Dumbbell,
  House,
  Library,
  UserRound,
  ArrowUpRight,
  ArrowRight,
  WifiOff,
  Check,
  Share2,
  Plus,
  Play,
  AlertTriangle,
} from "lucide-react";
import { read, write, type Snapshot } from "./storage";
import {
  exerciseSchema,
  groups,
  startSession,
  uid,
  type Catalog,
  type Data,
  type Exercise,
  type Media,
  type Profile,
  type Plan,
} from "./model";
import { Modal, CatalogView, ExerciseDetails, date, clock } from "./components";
import { ProfileView } from "./Profile";
import { TrainingView } from "./Training";
import { PlanView, Setup } from "./Plans";
export function App() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null),
    [catalog, setCatalog] = useState<Catalog | null>(null),
    [media, setMedia] = useState<Media | null>(null),
    [error, setError] = useState(""),
    [fatal, setFatal] = useState(""),
    [busy, setBusy] = useState(false),
    [generating, setGenerating] = useState(false),
    [offlineReady, setOfflineReady] = useState(false),
    [online, setOnline] = useState(navigator.onLine),
    [install, setInstall] = useState(false),
    [details, setDetails] = useState<Exercise | null>(null);
  const [route, setRoute] = useState(location.hash.slice(1) || "home");
  const live = useRef<Snapshot | null>(null),
    locked = useRef(false),
    worker = useRef<Worker | null>(null);
  const go = (page: string) => {
    location.hash = page;
  };
  useEffect(() => {
    const handler = () => {
      setRoute(location.hash.slice(1) || "home");
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", handler);
    return () => window.removeEventListener("hashchange", handler);
  }, []);
  useEffect(() => {
    let alive = true;
    Promise.all([
      read(),
      fetch("./catalog.json").then((r) => {
        if (!r.ok) throw Error();
        return r.json();
      }),
      fetch("./media.json").then((r) => {
        if (!r.ok) throw Error();
        return r.json();
      }),
    ])
      .then(([s, c, m]) => {
        if (!alive) return;
        c.exercises = c.exercises.map((e: unknown) => exerciseSchema.parse(e));
        if (!groups.every((g) => c.groups[g])) throw Error();
        live.current = s;
        setSnapshot(s);
        setCatalog(c);
        setMedia(m);
      })
      .catch(
        () =>
          alive &&
          setFatal(
            "Не удалось открыть приложение. Проверьте соединение и доступ к хранилищу браузера. Не очищайте данные сайта, если в нём уже есть записи.",
          ),
      );
    const connection = () => setOnline(navigator.onLine);
    window.addEventListener("online", connection);
    window.addEventListener("offline", connection);
    if ("serviceWorker" in navigator && import.meta.env.PROD)
      navigator.serviceWorker
        .register("./sw.js")
        .then(() => navigator.serviceWorker.ready)
        .then(() => {
          if (alive) setOfflineReady(true);
        })
        .catch(() => {});
    return () => {
      alive = false;
      window.removeEventListener("online", connection);
      window.removeEventListener("offline", connection);
    };
  }, []);
  useEffect(() => () => worker.current?.terminate(), []);
  async function update(fn: (d: Data) => Data): Promise<boolean> {
    if (locked.current || !live.current) return false;
    locked.current = true;
    setBusy(true);
    try {
      const s = live.current;
      const data = fn(structuredClone(s.data));
      const saved = await write(data, s.revision);
      live.current = saved;
      setSnapshot(saved);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось сохранить данные.");
      return false;
    } finally {
      locked.current = false;
      setBusy(false);
    }
  }
  function generate(profile: Profile) {
    if (generating || busy || !catalog || !live.current) return;
    setGenerating(true);
    const w = new Worker(new URL("./generator.worker.ts", import.meta.url), {
      type: "module",
    });
    worker.current = w;
    const timeout = setTimeout(() => {
      w.terminate();
      setGenerating(false);
      setError(
        "Составление программы заняло слишком много времени. Повторите попытку.",
      );
    }, 30000);
    w.onerror = () => {
      clearTimeout(timeout);
      w.terminate();
      setGenerating(false);
      setError("Не удалось запустить генератор.");
    };
    w.onmessage = async ({ data }) => {
      clearTimeout(timeout);
      w.terminate();
      if (data.error) {
        setError(data.error);
        setGenerating(false);
        return;
      }
      const plan: Plan = {
        ...data.plan,
        id: uid(),
        createdAtEpochMillis: Date.now(),
      };
      if (
        await update((d) => ({
          ...d,
          profile,
          plans: [plan, ...d.plans],
          activePlanId: plan.id,
        }))
      )
        go("plan");
      setGenerating(false);
    };
    w.postMessage({
      profile,
      preferences: live.current.data.preferences,
      catalog,
    });
  }
  if (fatal)
    return (
      <main className="boot">
        <AlertTriangle />
        <h1>Нужна ваша помощь</h1>
        <p>{fatal}</p>
        <button className="button" onClick={() => location.reload()}>
          Повторить
        </button>
      </main>
    );
  if (!snapshot || !catalog || !media)
    return (
      <main className="boot">
        <Dumbbell size={48} />
        <h1>Body Build</h1>
        <p>Подготавливаем ваше пространство…</p>
      </main>
    );
  const data = snapshot.data,
    activePlan =
      data.plans.find((p) => p.id === data.activePlanId) ?? data.plans[0];
  const startFree = async () => {
    if (data.active) {
      go("workout");
      return;
    }
    if (await update((d) => ({ ...d, active: startSession() }))) {
      navigator.storage?.persist?.().catch(() => {});
      go("workout");
    }
  };
  return (
    <div className="app-shell">
      <header className="app-header">
        <button className="brand" onClick={() => go("home")}>
          <span>
            <Dumbbell size={20} />
          </span>
          BODY BUILD
        </button>
        <button className="install-link" onClick={() => setInstall(true)}>
          <Share2 size={17} />
          <span>На iPhone</span>
        </button>
      </header>
      <main className="main">
        {route === "home" && (
          <>
            <div className="welcome">
              <p className="eyebrow">СИЛЬНЕЕ. В СВОЁМ РИТМЕ.</p>
              <h1>
                Ваша следующая
                <br />
                сильная версия<span>.</span>
              </h1>
              <p className="muted">
                Меньше планирования.
                <br />
                Больше внимания к себе.
              </p>
            </div>
            {data.active && (
              <button className="resume" onClick={() => go("workout")}>
                <Play size={23} />
                <span>
                  <b>Тренировка продолжается</b>
                  <small>
                    {data.active.completedSets.length} подходов записано
                  </small>
                </span>
                <ArrowRight size={20} />
              </button>
            )}
            <section className="hero">
              <div className="between">
                <span className="pill">БЕЗ ГОТОВОГО ПЛАНА</span>
                <ArrowUpRight size={28} />
              </div>
              <div className="hero-graphic" aria-hidden="true">
                <span />
                <span />
                <span />
                <Dumbbell />
              </div>
              <h2>
                Свободная
                <br />
                тренировка
              </h2>
              <p>
                Выбирайте упражнения по ходу занятия.
                <br />
                Мы сохраним каждый подход.
              </p>
              <button
                className="button dark full"
                disabled={busy}
                onClick={startFree}
              >
                {data.active ? "Продолжить занятие" : "Начать тренировку"}
                <ArrowRight size={19} />
              </button>
            </section>
            <section className="plan-entry">
              <div className="plan-icon">
                <Library />
              </div>
              <div>
                <h2>По вашему плану</h2>
                <p className="muted small">
                  {activePlan?.title ?? "Программа под ваши цели и время"}
                </p>
              </div>
              <button
                className="icon"
                aria-label="Открыть программу"
                onClick={() => go(activePlan ? "plan" : "setup")}
              >
                <ArrowUpRight />
              </button>
            </section>
            <div className="section-heading">
              <h2>Ваш путь</h2>
              <button className="subtle" onClick={() => go("profile")}>
                История <ArrowRight size={15} />
              </button>
            </div>
            <div className="stats home-stats">
              <div>
                <b>{data.history.length.toString().padStart(2, "0")}</b>
                <small>тренировок</small>
              </div>
              <div>
                <b>
                  {data.history.reduce((n, s) => n + s.completedSets.length, 0)}
                </b>
                <small>подходов</small>
              </div>
              <div>
                <b>{catalog.exercises.length}</b>
                <small>упражнения</small>
              </div>
            </div>
            {data.history[0] && (
              <p className="muted small">
                Последнее занятие: {date(data.history[0].startedAtEpochMillis)}{" "}
                ·{" "}
                {clock(
                  (data.history[0].completedAtEpochMillis! -
                    data.history[0].startedAtEpochMillis) /
                    1000,
                )}
              </p>
            )}
            <div className="local-status">
              {offlineReady ? (
                <Check size={16} />
              ) : !online ? (
                <WifiOff size={16} />
              ) : (
                <span className="status-dot" />
              )}
              <span>
                {offlineReady
                  ? "Приложение и фото доступны офлайн"
                  : !online
                    ? "Нет сети. Офлайн-кеш ещё не готов"
                    : import.meta.env.DEV
                      ? "Режим разработки · офлайн доступен в сборке"
                      : "Подготавливаем офлайн-режим. Не закрывайте приложение."}
              </span>
            </div>
          </>
        )}
        {route === "catalog" && (
          <>
            <p className="eyebrow">БИБЛИОТЕКА ДВИЖЕНИЙ</p>
            <h1>Найдите своё.</h1>
            <CatalogView
              catalog={catalog}
              media={media}
              onDetails={setDetails}
              preferences={data.preferences}
              onPreference={(id, kind) =>
                update((d) => {
                  if (kind) d.preferences[id] = kind;
                  else delete d.preferences[id];
                  return d;
                })
              }
            />
          </>
        )}
        {(route === "setup" || (route === "plan" && !activePlan)) && (
          <Setup
            profile={data.profile}
            catalog={catalog}
            busy={busy || generating}
            onGenerate={generate}
          />
        )}
        {route === "plan" && activePlan && (
          <PlanView
            key={activePlan.id}
            plan={activePlan}
            data={data}
            catalog={catalog}
            media={media}
            onDetails={setDetails}
            busy={busy}
            onSetup={() => go("setup")}
            onStart={async (i) => {
              if (data.active) {
                go("workout");
                return;
              }
              if (
                await update((d) => ({
                  ...d,
                  active: startSession(activePlan.days[i], activePlan),
                }))
              )
                go("workout");
            }}
            onUpdate={(plan) =>
              update((d) => ({
                ...d,
                plans: d.plans.map((p) => (p.id === plan.id ? plan : p)),
              }))
            }
          />
        )}
        {route === "workout" && (
          <TrainingView
            data={data}
            catalog={catalog}
            media={media}
            busy={busy}
            update={update}
            onDetails={setDetails}
            onDone={() => go("home")}
          />
        )}
        {route === "profile" && (
          <>
            <p className="eyebrow">РЕЗУЛЬТАТЫ, КОТОРЫЕ ОСТАЮТСЯ</p>
            <h1>Ваш прогресс.</h1>
            <ProfileView
              data={data}
              catalog={catalog}
              update={update}
              busy={busy}
              error={setError}
              openPlan={async (id) => {
                if (await update((d) => ({ ...d, activePlanId: id })))
                  go("plan");
              }}
            />
          </>
        )}
        {!["home", "catalog", "plan", "setup", "workout", "profile"].includes(
          route,
        ) && (
          <div className="empty">
            <h1>Страница не найдена</h1>
            <button className="button" onClick={() => go("home")}>
              На главную
            </button>
          </div>
        )}
      </main>
      <nav className="bottom-nav" aria-label="Основная навигация">
        {[
          { id: "home", label: "Главная", Icon: House },
          { id: "plan", label: "Программа", Icon: Library },
          { id: "catalog", label: "Каталог", Icon: Dumbbell },
          { id: "profile", label: "Профиль", Icon: UserRound },
        ].map(({ id, label, Icon }) => (
          <button
            key={id}
            aria-current={route === id ? "page" : undefined}
            onClick={() => go(id)}
          >
            <Icon size={21} />
            <span>{label}</span>
          </button>
        ))}
      </nav>
      {details && (
        <ExerciseDetails
          exercise={details}
          media={media}
          catalog={catalog}
          onClose={() => setDetails(null)}
        />
      )}
      {error && (
        <Modal
          title="Не удалось выполнить действие"
          onClose={() => setError("")}
        >
          <p role="alert">{error}</p>
          <button className="button full" onClick={() => setError("")}>
            Понятно
          </button>
        </Modal>
      )}
      {install && (
        <Modal title="Body Build на iPhone" onClose={() => setInstall(false)}>
          <div className="install-art">
            <Dumbbell size={52} />
          </div>
          <h2>Всегда под рукой.</h2>
          <ol className="install-steps">
            <li>Откройте HTTPS-адрес приложения в Safari.</li>
            <li>
              Нажмите «Поделиться» <Share2 size={16} /> → «На экран Домой»{" "}
              <Plus size={16} />.
            </li>
            <li>
              Если доступно, включите «Открывать как веб-приложение» и нажмите
              «Добавить».
            </li>
            <li>
              Откройте значок и дождитесь отметки «Приложение и фото доступны
              офлайн».
            </li>
          </ol>
          <p className="notice">
            Начинайте записывать тренировки уже в установленном приложении.
            Safari и приложение на главном экране могут иметь отдельные
            хранилища — переносите записи через резервную копию в профиле.
          </p>
          <p className="muted small">
            Офлайн-режим требует первого открытия с интернетом. Не используйте
            приватный режим. История не синхронизируется с Android.
          </p>
          <a
            href="https://support.apple.com/guide/iphone/open-as-web-app-iphea86e5236/ios"
            target="_blank"
            rel="noreferrer"
          >
            Инструкция Apple ↗
          </a>
        </Modal>
      )}
    </div>
  );
}
