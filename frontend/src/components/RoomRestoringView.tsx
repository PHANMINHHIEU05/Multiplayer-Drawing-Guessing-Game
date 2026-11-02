import React from "react";
import { ConnectionStatus } from "./ConnectionStatus";

interface RoomRestoringViewProps {
  roomId: string;
  isRestoring: boolean;
  error?: string | null;
  onGoHome: () => void;
}

export const RoomRestoringView: React.FC<RoomRestoringViewProps> = ({
  roomId,
  isRestoring,
  error,
  onGoHome,
}) => {
  return (
    <div className="dg-page min-h-screen flex flex-col justify-between py-3 sm:py-4 text-slate-800">
      <header className="dg-topbar flex items-center justify-between gap-3 mb-6">
        <div className="dg-brand">
          <span className="dg-brand-palette">🎨</span>
          <span className="dg-brand-name text-2xl sm:text-3xl">
            Dopamine<span className="dg-brand-dot">.io</span>
          </span>
        </div>
        <ConnectionStatus />
      </header>

      <main className="flex-1 flex flex-col items-center justify-center p-4">
        <div className="dg-home-card glass-panel w-full max-w-md p-6 sm:p-8 text-center animate-pop-in">
          {error ? (
            <div className="space-y-4">
              <div className="w-16 h-16 mx-auto rounded-full bg-rose-100 border-2 border-rose-300 flex items-center justify-center text-3xl shadow-inner">
                ⚠️
              </div>
              <div>
                <h2 className="text-lg sm:text-xl font-black text-rose-700">
                  Không thể kết nối lại phòng
                </h2>
                <span className="inline-block mt-1 px-3 py-0.5 rounded-lg bg-slate-100 text-slate-700 font-mono font-black text-xs border border-slate-300">
                  #{roomId}
                </span>
                <p className="text-xs font-semibold text-slate-600 mt-2 leading-relaxed">
                  {error}
                </p>
              </div>
              <button
                type="button"
                onClick={onGoHome}
                className="dg-primary-button bouncy-btn w-full py-2.5 text-xs sm:text-sm flex items-center justify-center gap-1.5 mt-2"
              >
                <span className="material-symbols-outlined text-base">home</span>
                <span>Quay về trang chủ</span>
              </button>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="relative w-16 h-16 mx-auto">
                <div className="w-16 h-16 rounded-full bg-sky-100 border-2 border-sky-300 flex items-center justify-center text-3xl shadow-inner animate-pulse">
                  🖌️
                </div>
                <div className="absolute inset-0 rounded-full border-2 border-sky-500 border-t-transparent animate-spin" />
              </div>
              <div>
                <h2 className="text-lg sm:text-xl font-black text-slate-800">
                  Đang kết nối lại phòng...
                </h2>
                <span className="inline-block mt-1 px-3 py-0.5 rounded-lg bg-[#15375f] text-white font-mono font-black text-xs shadow-sm">
                  #{roomId}
                </span>
                <p className="text-xs font-semibold text-slate-500 mt-2">
                  {isRestoring
                    ? "Đang đồng bộ phiên chơi và bàn vẽ của bạn..."
                    : "Đang chờ kết nối máy chủ..."}
                </p>
              </div>
              <div className="pt-2">
                <button
                  type="button"
                  onClick={onGoHome}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors"
                >
                  Hủy & Quay về trang chủ
                </button>
              </div>
            </div>
          )}
        </div>
      </main>

      <footer className="text-center text-xs font-bold text-white/90 py-2 px-4 drop-shadow">
        Dopamine.io · Vẽ nhanh · Đoán chuẩn · Cười hết cỡ!
      </footer>
    </div>
  );
};
