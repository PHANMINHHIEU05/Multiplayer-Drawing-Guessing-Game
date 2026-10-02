import React from "react";
import { useNoticeStore, noticeStore, NoticeType } from "../store/noticeStore";

const getNoticeBadge = (type: NoticeType) => {
  switch (type) {
    case "SUCCESS":
      return {
        icon: "✓",
        classes:
          "bg-emerald-50/95 border-emerald-300 text-emerald-950 shadow-emerald-900/15",
      };
    case "WARNING":
      return {
        icon: "⚠️",
        classes:
          "bg-amber-50/95 border-amber-300 text-amber-950 shadow-amber-900/15",
      };
    case "ERROR":
      return {
        icon: "✕",
        classes:
          "bg-rose-50/95 border-rose-300 text-rose-950 shadow-rose-900/15",
      };
    case "INFO":
    default:
      return {
        icon: "ℹ️",
        classes:
          "bg-sky-50/95 border-sky-300 text-sky-950 shadow-sky-900/15",
      };
  }
};

export const GameSystemNotice: React.FC = () => {
  const notices = useNoticeStore((s) => s.notices);

  if (notices.length === 0) return null;

  return (
    <div className="fixed top-3 left-1/2 -translate-x-1/2 z-50 flex flex-col items-center gap-2 pointer-events-none max-w-md w-full px-4 select-none">
      {notices.map((notice) => {
        const badge = getNoticeBadge(notice.type);
        return (
          <div
            key={notice.id}
            className={`pointer-events-auto px-4 py-2.5 rounded-xl border-2 border-[#15375f] shadow-[0_4px_0_rgba(10,52,93,.32),0_12px_26px_rgba(10,52,93,.16)] flex items-center justify-between gap-3 text-xs sm:text-sm font-extrabold transition-all animate-pop-in ${badge.classes}`}
          >
            <div className="flex items-center gap-2">
              <span className="text-base leading-none">{badge.icon}</span>
              <span className="leading-snug">{notice.message}</span>
            </div>
            <button
              onClick={() => noticeStore.removeNotice(notice.id)}
              className="text-current opacity-50 hover:opacity-100 hover:bg-black/5 rounded-full w-5 h-5 flex items-center justify-center font-bold text-xs transition-all shrink-0 ml-1"
              aria-label="Dismiss notice"
            >
              ✕
            </button>
          </div>
        );
      })}
    </div>
  );
};
