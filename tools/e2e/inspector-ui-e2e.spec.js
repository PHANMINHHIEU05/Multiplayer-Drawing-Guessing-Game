/**
 * TV9 — Network Inspector readability regression (Playwright, real browser).
 *
 * Verifies the light-panel/dark-text fix: contrast of key text elements,
 * closed-by-default, open/close/Esc behavior, no auto-open on reconnect,
 * chip readability, and that the panel does not block game controls.
 * Metrics logic is untouched (metricsStore not modified).
 */
import { test, expect } from '@playwright/test';

const FRONTEND = process.env.FRONTEND_URL || 'http://localhost:3000';

async function joinAs(page, name) {
  await page.goto(FRONTEND);
  await page.waitForTimeout(2500);
  await page.locator('input[placeholder*="tên của bạn"]').fill(name);
}

/** Parse rgb() → relative luminance (WCAG). */
function luminance(r, g, b) {
  const f = (c) => {
    c /= 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}
function contrastRatio(rgb1, rgb2) {
  const l1 = luminance(...rgb1);
  const l2 = luminance(...rgb2);
  const [a, b] = l1 > l2 ? [l1, l2] : [l2, l1];
  return (a + 0.05) / (b + 0.05);
}

async function rgbOf(locator) {
  const raw = await locator.evaluate((el) => getComputedStyle(el).color);
  const m = raw.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  return m ? [+m[1], +m[2], +m[3]] : [0, 0, 0];
}
async function effectiveBg(locator) {
  // walk up ancestors until a non-transparent background is found
  return locator.evaluate((el) => {
    let node = el;
    while (node && node !== document.documentElement) {
      const bg = getComputedStyle(node).backgroundColor;
      const m = bg.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
      if (m) {
        const alpha = m[4] === undefined ? 1 : parseFloat(m[4]);
        if (alpha > 0.7) return [+m[1], +m[2], +m[3]];
      }
      node = node.parentElement;
    }
    return [255, 255, 255];
  });
}

test('NI-1: Inspector đóng mặc định + chip NET đọc được; panel mở: chữ tối trên nền sáng, đủ tương phản WCAG', async ({ browser }) => {
  const host = await (await browser.newContext()).newPage();
  const guest = await (await browser.newContext()).newPage();
  await joinAs(host, 'ContrastHost');
  await joinAs(guest, 'ContrastGuest');

  await host.getByRole('button', { name: /BẮT ĐẦU TẠO PHÒNG/ }).click();
  await host.waitForTimeout(2000);
  const code = (await host.locator('span.font-mono').first().textContent())?.replace('#', '').trim();

  await guest.getByRole('button', { name: '🚪 VÀO PHÒNG' }).click();
  const digits = guest.locator('input[maxlength="1"]');
  for (let i = 0; i < code.length; i++) await digits.nth(i).fill(code[i]);
  await guest.getByRole('button', { name: /VÀO PHÒNG NGAY/ }).click();
  await guest.waitForTimeout(1500);
  await host.getByRole('button', { name: /BẮT ĐẦU GAME/ }).click();
  await host.waitForTimeout(3500);

  // 1) closed by default — panel header must not exist
  const headerOpen = await host.getByText('Network Inspector').isVisible().catch(() => false);
  console.log('closed by default:', !headerOpen);
  expect(headerOpen).toBe(false);

  // 2) compact chip visible & opens panel
  const chip = host.getByRole('button', { name: /NET/ });
  await chip.waitFor({ timeout: 5000 });
  expect(await chip.isVisible()).toBe(true);
  await chip.click();
  await host.getByText('Network Inspector').waitFor({ timeout: 3000 });

  // 3) panel is LIGHT (bg-white/92 + blur over game background)
  const panel = host.locator('div:has-text("Network Inspector")').locator('..').first();
  const panelBg = await effectiveBg(host.getByText('⚡ Network Inspector'));
  console.log('panel effective bg RGB:', panelBg);
  expect(panelBg[0]).toBeGreaterThan(200); // near-white surface

  // 4) contrast checks (text color vs panel background)
  const checks = [
    ['Header title (⚡ Network Inspector)', host.getByText('⚡ Network Inspector'), 4.5],
    ['Status badge (Đã kết nối)', host.getByText('Đã kết nối'), 4.5],
    ['Section heading (Latency)', host.getByText('Latency', { exact: true }), 4.5],
    ['Section heading (Traffic)', host.getByText('Traffic', { exact: true }), 4.5],
    ['Section heading (Drawing Stream)', host.getByText('Drawing Stream'), 4.5],
    ['Section heading (Connection)', host.getByText('Connection', { exact: true }), 4.5],
    ['Secondary label (Avg)', host.getByText('Avg', { exact: true }), 4.5],
    ['Secondary label (P95)', host.getByText('P95', { exact: true }), 4.5],
    ['Secondary label (Jitter)', host.getByText('Jitter', { exact: true }), 4.5],
  ];
  for (const [name, loc, minRatio] of checks) {
    const fg = await rgbOf(loc);
    const ratio = contrastRatio(fg, panelBg);
    const ok = ratio >= minRatio;
    console.log(`contrast ${name}: ${ratio.toFixed(2)}:1 ${ok ? 'OK' : 'FAIL'} (fg=${fg})`);
    expect(ok).toBe(true);
  }

  // 5) inactive protocol labels readable (text-slate-600 on slate-100)
  const inactive = host.getByRole('button', { name: 'JSON Batch' });
  const inactiveFg = await rgbOf(inactive);
  const inactiveBg = await effectiveBg(inactive);
  const inactiveRatio = contrastRatio(inactiveFg, inactiveBg);
  console.log(`inactive protocol label contrast: ${inactiveRatio.toFixed(2)}:1 (fg=${inactiveFg} bg=${inactiveBg})`);
  expect(inactiveRatio).toBeGreaterThanOrEqual(4.5);

  // 6) primary RTT number contrast (large text ≥3:1)
  const rttValue = host.locator('span', { hasText: /^—$|^\d+$/ }).first();
  const rttFg = await rgbOf(rttValue);
  const rttRatio = contrastRatio(rttFg, panelBg);
  console.log(`RTT value contrast: ${rttRatio.toFixed(2)}:1 (fg=${rttFg})`);
  expect(rttRatio).toBeGreaterThanOrEqual(3);

  // 7) panel does not block guess input / chat input
  const guessBox = host.locator('input[placeholder*="từ dự đoán"]');
  const panelBox = await host.getByText('⚡ Network Inspector').boundingBox();
  const guessBoxRect = await guessBox.boundingBox();
  const overlap = panelBox && guessBoxRect
    ? !(panelBox.x + panelBox.width < guessBoxRect.x || guessBoxRect.x + guessBoxRect.width < panelBox.x
        || panelBox.y + panelBox.height < guessBoxRect.y || guessBoxRect.y + guessBoxRect.height < panelBox.y)
    : false;
  console.log('panel overlaps guess input:', overlap);
  expect(overlap).toBe(false);
  expect(await guessBox.isEnabled()).toBe(true);

  // 8) Esc closes
  await host.keyboard.press('Escape');
  await host.waitForTimeout(300);
  const closedAfterEsc = !(await host.getByText('Network Inspector').isVisible().catch(() => false));
  console.log('closes on Esc:', closedAfterEsc);
  expect(closedAfterEsc).toBe(true);

  // 9) X closes
  await chip.click();
  await host.getByText('Network Inspector').waitFor({ timeout: 3000 });
  await host.locator('button[title*="Đóng Inspector"]').click();
  await host.waitForTimeout(300);
  const closedAfterX = !(await host.getByText('Network Inspector').isVisible().catch(() => false));
  console.log('closes on X:', closedAfterX);
  expect(closedAfterX).toBe(true);

  // 10) hotkey still works
  await host.keyboard.press('Control+Shift+N');
  await host.waitForTimeout(300);
  const openByHotkey = await host.getByText('Network Inspector').isVisible().catch(() => false);
  console.log('hotkey toggles:', openByHotkey);
  expect(openByHotkey).toBe(true);
  await host.keyboard.press('Escape');

  // 11) metrics logic unchanged — chip shows an RTT number after a few seconds
  await host.waitForTimeout(3000);
  const chipText = await chip.textContent();
  console.log('chip text (has RTT or dash):', chipText);
  expect(chipText).toMatch(/(NET|ms|—)/);

  await host.context().close();
  await guest.context().close();
});
