import { dataSchema, emptyData, type Data } from "./model";
export interface Snapshot {
  revision: number;
  data: Data;
}
function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open("body-build", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("state");
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    request.onblocked = () =>
      reject(new Error("Закройте другие вкладки Body Build и повторите."));
  });
}
export async function read(): Promise<Snapshot> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction("state", "readonly"),
      req = tx.objectStore("state").get("main");
    let value: Snapshot;
    req.onsuccess = () => {
      try {
        value = req.result
          ? {
              revision: req.result.revision,
              data: dataSchema.parse(req.result.data),
            }
          : { revision: 0, data: emptyData() };
      } catch {
        reject(
          new Error(
            "Не удалось прочитать записи. Не очищайте данные браузера; восстановите резервную копию.",
          ),
        );
      }
    };
    tx.oncomplete = () => {
      db.close();
      if (value) resolve(value);
    };
    tx.onerror = () => {
      db.close();
      reject(tx.error);
    };
  });
}
export async function write(data: Data, revision: number): Promise<Snapshot> {
  const parsed = dataSchema.parse(data);
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction("state", "readwrite"),
      store = tx.objectStore("state"),
      req = store.get("main");
    let conflict = false;
    req.onsuccess = () => {
      if ((req.result?.revision ?? 0) !== revision) {
        conflict = true;
        tx.abort();
        return;
      }
      store.put({ revision: revision + 1, data: parsed }, "main");
    };
    tx.oncomplete = () => {
      db.close();
      resolve({ revision: revision + 1, data: parsed });
    };
    tx.onabort = tx.onerror = () => {
      db.close();
      reject(
        new Error(
          conflict
            ? "Данные изменились в другой вкладке. Перезагрузите страницу перед продолжением."
            : "Не удалось сохранить: возможно, закончилось место. Действие не записано. Сделайте резервную копию.",
        ),
      );
    };
  });
}
