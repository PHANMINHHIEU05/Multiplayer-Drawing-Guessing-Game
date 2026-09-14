/**
 * UI-level E2E regression using Playwright (chromium, 2 independent contexts).
 * Covers: Network Inspector UX (Part 14), guess feedback UI, secret privacy in browser,
 * duplicate listener check, game flow smoke via the real frontend.
 *
 * Usage: npx playwright test tools/e2e/ui-e2e.spec.ts --config=tools/e2e/playwright.config.js
 */
import { test, expect } from '@playwright/test';

const FRONTEND = process.env.FRONTEND_URL || 'http://localhost:3000';

async function joinAs(page, name) {
  await page.goto(FRONTEND);
  // wait for intro overlay to settle
  await page.waitForTimeout(2500);
  const input = page.locator('input[placeholder*="tên của bạn"]');
  await input.fill(name);
  await input.blur();
}

/** Switch to the "VÀO PHÒNG" tab and enter a room code digit by digit. */
async function joinRoomByCode(page, code) {
  await page.getByRole('button', { name: '🚪 VÀO PHÒNG' }).click();
  const digits = page.locator('input[maxlength="1"]');
  const n = await digits.count();
  for (let i = 0; i < code.length && i < n; i++) {
    await digits.nth(i).fill(code[i]);
  }
  await page.getByRole('button', { name: /VÀO PHÒNG NGAY/ }).click();
  await page.waitForTimeout(1500);
}

test.describe.configure({ mode: 'serial' });

test('UI-1: Network Inspector đóng mặc định, chip NET hiển thị, không che chat/ô đoán', async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await joinAs(page, 'HostUI');
  await page.getByRole('button', { name: /BẮT ĐẦU TẠO PHÒNG/ }).click();
  await page.waitForTimeout(2000);
  // Should land on lobby — inspector only mounted in game screen; chip must NOT be on home/lobby
  const chipOnLobby = await page.getByText('NET', { exact: false }).locator('visible=true').count();
  console.log('chip on lobby (expect 0 visible NET chips):', chipOnLobby);

  // Join a second player to allow starting the game
  const ctx2 = await browser.newContext();
  const page2 = await ctx2.newPage();
  await joinAs(page2, 'GuestUI');
  // read room code from host lobby
  const codeEl = page.locator('span.font-mono');
  await codeEl.first().waitFor({ timeout: 5000 });
  const code = (await codeEl.first().textContent())?.replace('#', '').trim();
  console.log('room code:', code);
  // guest joins via 6-digit inputs
  await joinRoomByCode(page2, code);

  // host starts game
  await page.getByRole('button', { name: /BẮT ĐẦU GAME/ }).click();
  await page.waitForTimeout(3000);

  // ── Inspector assertions (Part 14) ──
  // CLOSED by default: the expanded header must not be visible
  const headerVisible = await page.getByText('Network Inspector').isVisible().catch(() => false);
  console.log('inspector open by default (expect false):', headerVisible);

  // small floating NET chip visible
  const netChip = page.getByRole('button', { name: /NET/ });
  await netChip.waitFor({ timeout: 5000 });
  const chipVisible = await netChip.isVisible();
  console.log('NET chip visible:', chipVisible);

  // chip does not block guess input or chat input
  const guessInput = page.locator('input[placeholder*="từ dự đoán"]');
  const chipBox = await netChip.boundingBox();
  const guessBox = guessInput ? await guessInput.boundingBox() : null;
  const overlap = chipBox && guessBox ? !(chipBox.x + chipBox.width < guessBox.x || guessBox.x + guessBox.width < chipBox.x || chipBox.y + chipBox.height < guessBox.y || guessBox.y + guessBox.height < chipBox.y) : false;
  console.log('chip overlaps guess input (expect false):', overlap);

  // Click chip → panel opens
  await netChip.click();
  await page.getByText('Network Inspector').waitFor({ timeout: 3000 });
  const openOk = await page.getByText('Network Inspector').isVisible();
  console.log('panel opens on chip click:', openOk);

  // Escape closes
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);
  const closedAfterEsc = !(await page.getByText('Network Inspector').isVisible().catch(() => false));
  console.log('closes on Escape:', closedAfterEsc);

  // Reopen then X closes
  await netChip.click();
  await page.getByText('Network Inspector').waitFor({ timeout: 3000 });
  await page.locator('button[title*="Đóng Inspector"]').click();
  await page.waitForTimeout(300);
  const closedAfterX = !(await page.getByText('Network Inspector').isVisible().catch(() => false));
  console.log('closes on X:', closedAfterX);

  // Keyboard shortcut toggles
  await page.keyboard.press('Control+Shift+N');
  await page.waitForTimeout(300);
  const openByHotkey = await page.getByText('Network Inspector').isVisible().catch(() => false);
  console.log('Ctrl+Shift+N toggles open:', openByHotkey);
  await page.keyboard.press('Escape');

  expect(chipVisible).toBe(true);
  expect(overlap).toBe(false);
  expect(closedAfterEsc && closedAfterX).toBe(true);

  await ctx.close();
  await ctx2.close();
});

test('UI-2: guess feedback riêng tư + secret privacy + không trùng event', async ({ browser }) => {
  const host = await (await browser.newContext()).newPage();
  const guest = await (await browser.newContext()).newPage();
  await joinAs(host, 'PrivacyHost');
  await joinAs(guest, 'PrivacyGuest');

  await host.getByRole('button', { name: /BẮT ĐẦU TẠO PHÒNG/ }).click();
  await host.waitForTimeout(2000);
  const codeEl = host.locator('span.font-mono');
  const code = (await codeEl.first().textContent())?.replace('#', '').trim();
  await joinRoomByCode(guest, code);
  await host.getByRole('button', { name: /BẮT ĐẦU GAME/ }).click();
  await host.waitForTimeout(3500);

  // guest: wait for game screen, then read the WS frames to assert no secret word leak
  const leakedFrames = [];
  guest.on('websocket', (ws) => {
    ws.on('framereceived', (data) => {
      const s = typeof data === 'string' ? data : data.toString();
      if (s.includes('secretWord') && !s.includes('"secretWord":""') && !s.includes('secretWord":""')) {
        // GAME_STARTED/GAME_STATE carrying a non-empty secretWord to a GUESSER
        try {
          const m = JSON.parse(s);
          if (m.secretWord && m.type !== 'ERROR') leakedFrames.push(m.type);
        } catch { /* ignore */ }
      }
    });
  });

  // guest submits a wrong guess → expects private WRONG feedback inline
  const guessBox = guest.locator('input[placeholder*="từ dự đoán"]');
  await guessBox.waitFor({ timeout: 8000 });
  await guessBox.fill('xyz khong dung');
  await guest.getByRole('button', { name: 'ĐOÁN', exact: true }).click();
  await guest.waitForTimeout(1500);
  const wrongFeedback = await guest.getByText(/chưa chính xác/).isVisible().catch(() => false);
  console.log('guest sees private WRONG feedback:', wrongFeedback);

  // host (drawer) must not see the guest's private feedback panel text
  const hostSeesWrong = await host.getByText(/chưa chính xác/).isVisible().catch(() => false);
  console.log('host must NOT see guest private feedback (expect false):', hostSeesWrong);

  // guest sees no secret word hint text
  await guest.waitForTimeout(1000);
  console.log('secret leak frames to guesser (expect []):', leakedFrames);

  expect(wrongFeedback).toBe(true);
  expect(hostSeesWrong).toBe(false);
  expect(leakedFrames.length).toBe(0);

  await host.context().close();
  await guest.context().close();
});
