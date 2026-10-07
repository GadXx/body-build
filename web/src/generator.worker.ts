import { generate } from "./generator";
self.onmessage = ({ data }) => {
  try {
    self.postMessage({
      plan: generate(data.profile, data.preferences, data.catalog),
    });
  } catch {
    self.postMessage({
      error: "Не удалось составить программу. Проверьте настройки.",
    });
  }
};
