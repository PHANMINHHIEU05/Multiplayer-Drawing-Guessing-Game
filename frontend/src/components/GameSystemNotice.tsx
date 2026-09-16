import React from "react";
import { useNoticeStore, noticeStore, NoticeType } from "../store/noticeStore";

const getNoticeBadge = (type: NoticeType) => {
  switch (type) {
    case "SUCCESS":
      return {
        icon: "✓",
        classes:
          "bg-emerald-500/90 border-emerald-400/60 text-white shadow-emerald-500/20",
      };
    case "WARNING":
      return {
        icon: "⚠️",
        classes:
          "bg-amber-500/95 border-amber-400/60 text-slate-950 shadow-amber-500/20",
      };
    case "ERROR":
      return {
        icon: "✕",
        classes:
          "bg-rose-500/95 border-rose-400/60 text-white shadow-rose-500/20",
      };
    case "INFO":
    default:
      return {
        icon: "ℹ️",
        classes:
          "bg-indigo-600/90 border-indigo-400/50 text-white shadow-indigo-600/20",
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
            className={`pointer-events-auto px-4 py-2.5 rounded-2xl border backdrop-blur-md shadow-xl flex items-center justify-between gap-3 text-xs sm:text-sm font-extrabold transition-all animate-pop-in ${badge.classes}`}
          >
            <div className="flex items-center gap-2">
              <span className="text-base leading-none">{badge.icon}</span>
              <span className="leading-snug">{notice.message}</span>
            </div>
            <button
              onClick={() => noticeStore.removeNotice(notice.id)}
              className="text-white/70 hover:text-white rounded-full w-5 h-5 flex items-center justify-center font-bold text-xs transition-colors shrink-0 ml-1"
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
