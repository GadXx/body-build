import "fake-indexeddb/auto";
import { it, expect } from "vitest";
import { read, write } from "../src/storage";
it("persists snapshots and rejects stale tabs without overwriting newer data", async () => {
  const initial = await read();
  expect(initial.revision).toBe(0);
  const data = {
    ...initial.data,
    profile: { ...initial.data.profile, weightKg: 82 },
  };
  await write(data, 0);
  expect((await read()).data.profile.weightKg).toBe(82);
  await expect(write(initial.data, 0)).rejects.toThrow("другой вкладке");
  expect((await read()).data.profile.weightKg).toBe(82);
});
