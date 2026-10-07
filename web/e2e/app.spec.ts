import { test, expect } from "@playwright/test";
import { staticServer } from "./static-server";
import { readFile } from "node:fs/promises";
test("free session, reload, history analysis, body parameters and last-set defaults", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /Ваша следующая/ }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Начать тренировку", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Выбрать упражнение", exact: true })
    .click();
  await page
    .getByRole("textbox", { name: "Поиск упражнений" })
    .fill("EZ-грифом");
  await page.getByRole("button", { name: "Добавить подход" }).first().click();
  await page
    .getByRole("textbox", { name: "Повторения", exact: true })
    .fill("12");
  await page.getByLabel("Вес, кг", { exact: true }).fill("25");
  await page
    .getByRole("button", { name: "Подход выполнен", exact: true })
    .click();
  await expect(page.getByText("ВРЕМЯ ВОССТАНОВИТЬСЯ")).toBeVisible();
  await page.reload();
  await expect(page.getByText("ВРЕМЯ ВОССТАНОВИТЬСЯ")).toBeVisible();
  page.on("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Завершить и сохранить", exact: true })
    .click();
  await page.getByRole("button", { name: "Профиль", exact: true }).click();
  await page.getByRole("button", { name: "Прогресс", exact: true }).click();
  await page.locator(".progress-row").first().click();
  await expect(page.getByRole("heading", { name: /Индекс/ })).toBeVisible();
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("button", { name: "История", exact: true }).click();
  await page.getByRole("checkbox").first().check();
  await page.getByRole("button", { name: "Анализ нагрузки · 1" }).click();
  await expect(
    page.getByRole("heading", { name: "Мышечное покрытие" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("button", { name: "Изменить", exact: true }).click();
  await page.getByLabel("Рост, см").fill("181");
  await page.getByLabel("Вес, кг", { exact: true }).fill("82,5");
  await page.getByRole("button", { name: "Сохранить параметры" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole("heading", { name: /181/ })).toBeVisible();
  await page.getByRole("button", { name: "Главная", exact: true }).click();
  await page
    .getByRole("button", { name: "Начать тренировку", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Выбрать упражнение", exact: true })
    .click();
  await page
    .getByRole("textbox", { name: "Поиск упражнений" })
    .fill("EZ-грифом");
  await page.getByRole("button", { name: "Добавить подход" }).first().click();
  await expect(
    page.getByRole("textbox", { name: "Повторения", exact: true }),
  ).toHaveValue("12");
  await expect(page.getByLabel("Вес, кг", { exact: true })).toHaveValue("25");
  await page.screenshot({
    path: `test-results/workout-${test.info().project.name}.png`,
    fullPage: true,
  });
});

test("backup export and restore preserve profile; invalid imports are rejected", async ({
  page,
}) => {
  await page.addInitScript(() =>
    Object.defineProperty(navigator, "canShare", {
      value: () => false,
      configurable: true,
    }),
  );
  await page.goto("/#profile");
  await page.getByRole("button", { name: "Изменить", exact: true }).click();
  await page.getByLabel("Вес, кг", { exact: true }).fill("85");
  await page.getByRole("button", { name: "Сохранить параметры" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const downloaded = page.waitForEvent("download");
  await page.getByRole("button", { name: "Сохранить копию" }).click();
  const download = await downloaded;
  const path = (await download.path())!;
  expect(JSON.parse(await readFile(path, "utf8")).profile.weightKg).toBe(85);
  await page.getByRole("button", { name: "Изменить", exact: true }).click();
  await page.getByLabel("Вес, кг", { exact: true }).fill("79");
  await page.getByRole("button", { name: "Сохранить параметры" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  page.on("dialog", (dialog) => dialog.accept());
  await page.locator('input[type="file"]').setInputFiles(path);
  await expect(page.getByRole("heading", { name: /85/ })).toBeVisible();
  await page
    .locator('input[type="file"]')
    .setInputFiles({
      name: "broken.json",
      mimeType: "application/json",
      buffer: Buffer.from('{"version":999}'),
    });
  await expect(page.getByRole("alert")).toContainText("повреждённый файл");
  await page.getByRole("button", { name: "Понятно", exact: true }).click();
  await page.reload();
  await expect(page.getByRole("heading", { name: /85/ })).toBeVisible();
});
test("generation, photos, persisted plan and offline reload", async ({
  page,
}) => {
  const server = await staticServer();
  try {
    await page.goto(server.url);
    await expect(
      page.getByText("Приложение и фото доступны офлайн"),
    ).toBeVisible({ timeout: 45000 });
    await expect
      .poll(() =>
        page.evaluate(() => navigator.serviceWorker.controller?.state),
      )
      .toBe("activated");
    await page.screenshot({
      path: `test-results/home-${test.info().project.name}.png`,
      fullPage: true,
    });
    await page.getByRole("button", { name: "Программа", exact: true }).click();
    await page.getByLabel("Минут на занятие").selectOption("30");
    await page.getByRole("button", { name: "Составить и сохранить" }).click();
    await expect(
      page.getByRole("heading", { name: /Full body/ }),
    ).toBeVisible();
    server.close();
    await page.reload();
    await expect(
      page.getByRole("heading", { name: /Full body/ }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Каталог", exact: true }).click();
    await page.getByRole("textbox", { name: "Поиск упражнений" }).fill("икры");
    await expect(page.locator(".exercise-row")).not.toHaveCount(0);
    const img = page.locator(".exercise-row img").first();
    await expect(img).toBeVisible();
    await expect
      .poll(() =>
        img.evaluate((i: HTMLImageElement) => i.complete && i.naturalWidth > 0),
      )
      .toBe(true);
    await page.screenshot({
      path: `test-results/catalog-${test.info().project.name}.png`,
      fullPage: true,
    });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  } finally {
    server.close();
  }
});
