import React from "react";
import {
  useMatchSummaryStore,
  matchSummaryStore,
} from "../store/matchSummaryStore";
import { usePlayerStore } from "../store/playerStore";
import { Scoreboard } from "./Scoreboard";

export const MatchRecapModal: React.FC = () => {
  const { summary, isOpen } = useMatchSummaryStore((s) => s);
  const { playerId } = usePlayerStore((s) => s);

  if (!isOpen || !summary) {
    return null;
  }

  const handleClose = () => {
    matchSummaryStore.close();
  };

  const sortedScores = [...(summary.scores || [])].sort(
    (a, b) => b.score - a.score,
  );
  const winner = summary.winner || sortedScores[0];

  return (
    <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fadeIn">
      <div className="glass-panel-dark border-2 border-amber-400/80 rounded-3xl p-6 max-h-[90vh] max-w-md w-full overflow-y-auto text-center space-y-4 shadow-2xl relative">
        {/* Close button at top right */}
        <button
          onClick={handleClose}
          className="absolute top-4 right-4 text-white/60 hover:text-white bg-white/10 hover:bg-white/20 w-8 h-8 rounded-full flex items-center justify-center transition-all font-black text-sm"
          title="Đóng bảng tổng kết"
        >
          ✕
        </button>

        <div className="text-5xl sm:text-6xl animate-bounce pt-2">🏆</div>
        <div>
          <h2 className="text-2xl sm:text-3xl font-black bubbly-logo text-amber-300">
            TỔNG KẾT TRẬN ĐẤU!
          </h2>
          <p className="text-xs font-bold text-slate-300 mt-0.5">
            Bảng điểm chung cuộc
          </p>
        </div>

        {winner && (
          <div className="bg-amber-400/20 border border-amber-400/50 rounded-2xl py-2 px-4">
            <p className="text-sm font-black text-white">
              🥇 Người thắng:{" "}
              <span className="text-amber-300">
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
          <div className="rounded-2xl border border-amber-300/30 bg-white/5 p-3 text-left">
            <p className="mb-2 text-[10px] sm:text-xs font-black tracking-widest text-amber-200 uppercase">
              🎖️ DANH HIỆU TRẬN ĐẤU
            </p>
            <div className="space-y-1.5">
              {summary.awards.map((award, i) => (
                <div
                  key={`${award.type}-${award.playerId}-${i}`}
                  className="flex justify-between gap-3 text-xs"
                >
                  <span className="font-bold text-white/80">{award.label}</span>
                  <span className="text-right font-black text-amber-100">
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
            onClick={handleClose}
            className="bouncy-btn w-full py-3.5 bg-emerald-500 hover:bg-emerald-600 text-white font-black text-sm rounded-2xl shadow-[0_4px_0_0_#059669] transition-all flex items-center justify-center gap-2 hover:scale-[1.02]"
          >
            <span>ĐÓNG (VÀO PHÒNG CHỜ)</span>
            <span className="text-lg">✓</span>
          </button>
        </div>
      </div>
    </div>
  );
};
