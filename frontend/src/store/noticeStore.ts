import { useSyncExternalStore } from "react";

export type NoticeType = "INFO" | "SUCCESS" | "WARNING" | "ERROR";

export interface SystemNotice {
  id: string;
  type: NoticeType;
  message: string;
  durationMs: number;
  timestamp: number;
}

interface NoticeState {
  notices: SystemNotice[];
}

let state: NoticeState = {
  notices: [],
};

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((l) => l());
}

// Track recently pushed messages to prevent spam/self-echo duplicates
const recentMessages = new Map<string, number>();
const noticeTimers = new Map<string, ReturnType<typeof setTimeout>>();

export const noticeStore = {
  getState: () => state,

  pushNotice: (notice: {
    id?: string;
    type?: NoticeType;
    message: string;
    durationMs?: number;
  }) => {
    const msg = (notice.message || "").trim();
    if (!msg) return;

    const now = Date.now();
    const lastSeen = recentMessages.get(msg);
    // Deduplicate identical notices within 1500ms
    if (lastSeen && now - lastSeen < 1500) {
      return;
    }
    recentMessages.set(msg, now);

    // Clean up older keys in recentMessages
    if (recentMessages.size > 50) {
      for (const [k, t] of recentMessages.entries()) {
        if (now - t > 10000) recentMessages.delete(k);
      }
    }

    const id =
      notice.id || `notice_${now}_${Math.random().toString(36).slice(2, 7)}`;
    const durationMs = notice.durationMs ?? 3000;
    const item: SystemNotice = {
      id,
      type: notice.type || "INFO",
      message: msg,
      durationMs,
      timestamp: now,
    };

    // Replace if id already exists, otherwise append keeping at most 3
    const filtered = state.notices.filter((n) => n.id !== id);
    state = { notices: [...filtered.slice(-2), item] };
    notify();

    const previousTimer = noticeTimers.get(id);
    if (previousTimer) clearTimeout(previousTimer);
    if (durationMs > 0) {
      const timer = setTimeout(() => {
        noticeTimers.delete(id);
        noticeStore.removeNotice(id);
      }, durationMs);
      noticeTimers.set(id, timer);
    }
  },

  removeNotice: (id: string) => {
    const timer = noticeTimers.get(id);
    if (timer) {
      clearTimeout(timer);
      noticeTimers.delete(id);
    }
    if (!state.notices.some((n) => n.id === id)) return;
    state = { notices: state.notices.filter((n) => n.id !== id) };
    notify();
  },

  clearAll: () => {
    noticeTimers.forEach((timer) => clearTimeout(timer));
    noticeTimers.clear();
    state = { notices: [] };
    notify();
  },

  subscribe: (listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useNoticeStore<T>(selector: (state: NoticeState) => T): T {
  return useSyncExternalStore(
    noticeStore.subscribe,
    () => selector(noticeStore.getState()),
    () => selector(noticeStore.getState()),
  );
}
