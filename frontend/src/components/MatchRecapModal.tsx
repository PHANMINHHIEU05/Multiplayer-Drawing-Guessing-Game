import React, { useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  useMatchSummaryStore,
  matchSummaryStore,
} from "../store/matchSummaryStore";
import { usePlayerStore } from "../store/playerStore";
import { roomStore } from "../store/roomStore";
import { Scoreboard } from "./Scoreboard";

export const MatchRecapModal: React.FC = () => {
  const { summary, isOpen } = useMatchSummaryStore((s) => s);
  const { playerId } = usePlayerStore((s) => s);
  const navigate = useNavigate();

  /**
   * The recap is shown after GAME_FINISHED, so closing it must always reveal the
   * waiting room instead of merely hiding an overlay above the finished game.
   */
  const handleClose = useCallback(() => {
    const room = roomStore.getState().room;
    if (room) {
      // The normal GAME_FINISHED handler already performs this update. Keeping
      // this small fallback makes the close action reliable if its GET_ROOM
      // refresh arrives late or an old server omits the WAITING transition.
      if (room.status !== "WAITING" && room.status !== "LOBBY") {
        roomStore.setRoom({ ...room, status: "WAITING" });
      }
      navigate("/lobby", { replace: true });
    } else {
      navigate("/", { replace: true });
    }
    matchSummaryStore.close();
  }, [navigate]);

  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") handleClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [handleClose, isOpen]);

  if (!isOpen || !summary) {
    return null;
  }

  const sortedScores = [...(summary.scores || [])].sort(
    (a, b) => b.score - a.score,
  );
  const winner = summary.winner || sortedScores[0];

  return (
    <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fadeIn">
      <div className="glass-panel-dark rounded-3xl p-6 max-h-[90vh] max-w-md w-full overflow-y-auto text-center space-y-4 shadow-2xl relative">
        {/* Close button at top right */}
        <button
          type="button"
          onClick={handleClose}
          className="absolute z-10 top-4 right-4 text-slate-500 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 w-9 h-9 rounded-full flex items-center justify-center transition-all font-black text-base cursor-pointer pointer-events-auto"
          title="Đóng bảng tổng kết"
          aria-label="Đóng tổng kết và vào phòng chờ"
        >
          ✕
        </button>

        <div className="text-5xl sm:text-6xl animate-bounce pt-2">🏆</div>
        <div>
          <h2 className="text-2xl sm:text-3xl font-black bubbly-logo text-amber-300">
            TỔNG KẾT TRẬN ĐẤU!
          </h2>
          <p className="text-xs font-bold text-slate-500 mt-0.5">
            Bảng điểm chung cuộc
          </p>
        </div>

        {winner && (
          <div className="bg-amber-100 border-2 border-amber-300 rounded-2xl py-2 px-4">
            <p className="text-sm font-black text-slate-800">
              🥇 Người thắng:{" "}
              <span className="text-amber-700">
                {winner.username || "Người chơi"}
              </span>{" "}
              ({winner.score} điểm)
            </p>
          </div>
        )}

        <div className="max-h-48 overflow-y-auto">
          <Scoreboard scores={summary.scores} currentPlayerId={playerId} />
        </div>

        {(summary.awards || []).length > 0 && (
          <div className="rounded-2xl border-2 border-amber-200 bg-amber-50 p-3 text-left">
            <p className="mb-2 text-[10px] sm:text-xs font-black tracking-widest text-amber-700 uppercase">
              🎖️ DANH HIỆU TRẬN ĐẤU
            </p>
            <div className="space-y-1.5">
              {summary.awards.map((award, i) => (
                <div
                  key={`${award.type}-${award.playerId}-${i}`}
                  className="flex justify-between gap-3 text-xs"
                >
                  <span className="font-bold text-slate-600">{award.label}</span>
                  <span className="text-right font-black text-amber-700">
                    {award.username}
                    {award.type === "FASTEST_GUESS" &&
                    typeof award.elapsedMillis === "number" &&
                    award.elapsedMillis > 0
                      ? ` · ${(award.elapsedMillis / 1000).toFixed(1)}s`
                      : award.type === "WINNER"
                        ? ` · ${award.value} điểm`
                        : award.type === "BEST_ARTIST"
                          ? ` · ${award.value} điểm vẽ`
                          : award.type === "MOST_CORRECT"
                            ? ` · ${award.value} lượt`
                            : award.type === "BEST_STREAK"
                              ? ` · ${award.value} vòng`
                              : ""}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Action Button: Đóng và vào phòng chờ */}
        <div className="pt-2">
          <button
            type="button"
            onClick={handleClose}
            className="dg-success-button bouncy-btn w-full py-3.5 text-sm flex items-center justify-center gap-2"
          >
            <span>ĐÓNG (VÀO PHÒNG CHỜ)</span>
            <span className="text-lg">✓</span>
          </button>
        </div>
      </div>
    </div>
  );
};
