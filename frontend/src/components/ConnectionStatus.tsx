import React from "react";
import { useConnectionStore } from "../store/connectionStore";
import { wsClient, resetAllSessionState } from "../websocket/WebSocketClient";

export const ConnectionStatus: React.FC = () => {
  const { status, lastError } = useConnectionStore((state) => state);

  const getStatusBadge = () => {
    switch (status) {
      case "CONNECTED":
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-white text-emerald-700 border-2 border-[#15375f] shadow-[0_2px_0_rgba(10,52,93,.35)]">
            <span className="w-2 h-2 rounded-full bg-emerald-300 animate-pulse" />
            Đã kết nối
          </span>
        );
      case "CONNECTING":
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-amber-50 text-amber-700 border-2 border-[#15375f] shadow-[0_2px_0_rgba(10,52,93,.35)]">
            <span className="w-2 h-2 rounded-full bg-amber-300 animate-ping" />
            Đang kết nối...
          </span>
        );
      case "RECONNECTING":
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-orange-50 text-orange-700 border-2 border-[#15375f] shadow-[0_2px_0_rgba(10,52,93,.35)]">
            <span className="w-2 h-2 rounded-full bg-orange-300 animate-bounce" />
            Mất kết nối — đang thử kết nối lại...
          </span>
        );
      case "FAILING_OVER":
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-indigo-50 text-indigo-700 border-2 border-[#15375f] shadow-[0_2px_0_rgba(10,52,93,.35)] animate-pulse">
            <span className="w-2 h-2 rounded-full bg-amber-300 animate-ping" />
            Đang chuyển sang máy chủ dự phòng...
          </span>
        );
      case "RECOVERING":
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-sky-50 text-sky-700 border-2 border-[#15375f] shadow-[0_2px_0_rgba(10,52,93,.35)]">
            <span className="w-2 h-2 rounded-full bg-sky-300 animate-ping" />
            Đang đồng bộ lại ván chơi...
          </span>
        );
      case "DISCONNECTED":
      default:
        return (
          <div className="inline-flex items-center gap-1.5">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-extrabold bg-rose-50 text-rose-700 border-2 border-[#15375f] shadow-[0_2px_0_rgba(10,52,93,.35)]">
              <span className="w-2 h-2 rounded-full bg-rose-400" />
              Không thể kết nối
            </span>
            <button
              onClick={() => wsClient.connect()}
              className="text-xs bg-white hover:bg-sky-50 text-[#15375f] font-bold px-2.5 py-1 rounded-lg transition-all shadow-sm border-2 border-[#15375f]"
              title="Thử kết nối lại"
            >
              Thử lại
            </button>
            <button
              onClick={() => resetAllSessionState()}
              className="text-xs bg-rose-500 hover:bg-rose-600 text-white font-bold px-2.5 py-1 rounded-lg transition-all shadow-sm border-2 border-[#15375f]"
              title="Quay lại trang chủ"
            >
              Về trang chủ
            </button>
          </div>
        );
    }
  };

  return (
    <div className="flex items-center gap-2 select-none">
      {getStatusBadge()}
      {lastError && (
        <span
          className="text-xs font-bold text-rose-900 bg-rose-50/95 border border-rose-300 px-2 py-0.5 rounded-lg max-w-xs truncate shadow-sm"
          title={lastError}
        >
          {lastError}
        </span>
      )}
    </div>
  );
};
