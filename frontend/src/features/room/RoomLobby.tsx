import React, { useEffect, useState } from "react";
import { useRoomStore } from "../../store/roomStore";
import { usePlayerStore, playerStore } from "../../store/playerStore";
import {
  wsClient,
  resetAllSessionState,
} from "../../websocket/WebSocketClient";
import { MessageType } from "../../websocket/protocol";
import { PlayerList } from "../../components/PlayerList";
import { ChatPanel } from "../chat/ChatPanel";
import { noticeStore } from "../../store/noticeStore";
import { translateError } from "../../utils/errorTranslation";

const CATEGORY_OPTIONS = [
  { id: "ANIMALS", label: "Động vật", icon: "🐾" },
  { id: "FOOD", label: "Đồ ăn", icon: "🍜" },
  { id: "OBJECTS", label: "Đồ vật", icon: "🧸" },
  { id: "PLACES", label: "Địa điểm", icon: "📍" },
  { id: "NATURE", label: "Thiên nhiên", icon: "🌿" },
  { id: "TECHNOLOGY", label: "Công nghệ", icon: "💻" },
] as const;

const sameCategories = (a: string[], b: string[]) =>
  [...a].sort().join(",") === [...b].sort().join(",");

/** TV10: readiness summary for the Start gating UI. Host is implicitly ready. */
function readinessSummary(room: {
  players: { playerId: string; ready?: boolean }[];
  hostPlayerId: string;
}) {
  const required = room.players.filter((p) => p.playerId !== room.hostPlayerId);
  const unready = required.filter((p) => !p.ready);
  return {
    allReady: required.length > 0 ? unready.length === 0 : true,
    unreadyCount: unready.length,
    requiredCount: required.length,
  };
}

export const RoomLobby: React.FC = () => {
  const room = useRoomStore((s) => s.room);
  const { playerId } = usePlayerStore((s) => s);
  const [starting, setStarting] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [togglingReady, setTogglingReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [categoryDraft, setCategoryDraft] = useState<string[]>(
    room?.selectedCategories || CATEGORY_OPTIONS.map((category) => category.id),
  );
  const [savingCategories, setSavingCategories] = useState(false);

  useEffect(() => {
    if (room?.selectedCategories) setCategoryDraft(room.selectedCategories);
  }, [room?.selectedCategories]);

  if (!room) return null;

  const isHost = room.hostPlayerId === playerId;
  const categoriesDirty = !sameCategories(
    categoryDraft,
    room.selectedCategories || [],
  );

  const handleCopyRoomCode = async () => {
    try {
      await navigator.clipboard.writeText(room.roomId);
      noticeStore.pushNotice({
        type: "SUCCESS",
        message: "Đã sao chép mã phòng",
        durationMs: 2500,
      });
    } catch {
      noticeStore.pushNotice({
        type: "INFO",
        message: `Mã phòng: ${room.roomId}`,
        durationMs: 3000,
      });
    }
  };

  const handleSaveCategories = async () => {
    if (categoryDraft.length === 0) {
      setError("Vui lòng chọn ít nhất một chủ đề từ khóa.");
      return;
    }
    setSavingCategories(true);
    setError(null);
    try {
      await wsClient.send(MessageType.SET_CATEGORIES, {
        selectedCategories: categoryDraft,
      });
      noticeStore.pushNotice({
        type: "SUCCESS",
        message: "Đã lưu cài đặt chủ đề",
        durationMs: 2000,
      });
    } catch (err: any) {
      setError(translateError(err));
    } finally {
      setSavingCategories(false);
    }
  };

  const handleStartGame = async () => {
    if (starting) return;
    setStarting(true);
    setError(null);
    try {
      await wsClient.send(MessageType.START_GAME, {
        roomId: room.roomId,
        playerId,
      });
    } catch (err: any) {
      setError(translateError(err));
    } finally {
      setStarting(false);
    }
  };

  // TV10: ready toggle (non-host) — server is authoritative, UI reflects PLAYER_READY_CHANGED
  const myReady =
    room.players.find((p) => p.playerId === playerId)?.ready ?? false;
  const handleToggleReady = async () => {
    if (togglingReady) return;
    setTogglingReady(true);
    try {
      await wsClient.send(MessageType.SET_READY, { ready: !myReady });
    } catch (err: any) {
      setError(translateError(err));
    } finally {
      setTogglingReady(false);
    }
  };

  // TV10: host kick (WAITING-only)
  const handleKick = async (targetPlayerId: string) => {
    try {
      await wsClient.send(MessageType.KICK_PLAYER, { targetPlayerId });
      noticeStore.pushNotice({
        type: "SUCCESS",
        message: "Đã mời người chơi ra khỏi phòng.",
        durationMs: 2500,
      });
    } catch (err: any) {
      setError(translateError(err));
    }
  };

  const handleLeaveRoom = async () => {
    setLeaving(true);
    try {
      await wsClient.send(MessageType.LEAVE_ROOM, {
        roomId: room.roomId,
        playerId,
        username: playerStore.getState().username,
      });
    } catch (err: any) {
      console.error("Leave room failed:", err);
    } finally {
      setLeaving(false);
      resetAllSessionState();
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 max-w-6xl mx-auto p-4">
      {/* Left Column: Room info & Player List */}
      <div className="lg:col-span-2 space-y-4">
        {/* Room Header Card */}
        <div className="glass-panel rounded-3xl p-5 shadow-2xl flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs px-3 py-1 rounded-xl bg-primary text-white font-mono font-black shadow-sm tracking-wider">
                #{room.roomId}
              </span>
              <button
                type="button"
                onClick={handleCopyRoomCode}
                className="text-xs font-black px-2.5 py-1 rounded-xl bg-white/90 hover:bg-white text-slate-700 border border-slate-200 shadow-sm transition-all flex items-center gap-1 hover:border-primary"
                title="Sao chép mã phòng"
              >
                <span>📋</span> Sao chép
              </button>
              <span className="text-xs px-2.5 py-1 rounded-xl bg-sky-100 text-sky-800 font-extrabold">
                {room.playerCount} / {room.maxPlayers} Người
              </span>
            </div>
            <h1 className="text-2xl font-black text-slate-800 mt-2">
              {room.name || `Phòng #${room.roomId}`}
            </h1>
            <p className="text-xs text-slate-500 font-bold mt-1">
              Số vòng:{" "}
              <span className="text-primary font-black">{room.roundCount}</span>{" "}
              • Thời gian vẽ:{" "}
              <span className="text-primary font-black">
                {room.roundDuration}s
              </span>
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              onClick={handleLeaveRoom}
              disabled={leaving}
              className="bouncy-btn px-4 py-2.5 bg-white/80 hover:bg-white text-rose-600 border border-rose-200 font-extrabold text-xs rounded-2xl transition-all shadow-sm disabled:opacity-50"
            >
              {leaving ? "Đang rời..." : "Rời phòng"}
            </button>

            {/* Non-host Ready toggle */}
            {!isHost && (
              <button
                onClick={handleToggleReady}
                disabled={togglingReady}
                className={`bouncy-btn px-5 py-2.5 font-black text-xs rounded-2xl transition-all border disabled:opacity-50 ${
                  myReady
                    ? "bg-emerald-500 hover:bg-emerald-600 text-white border-emerald-400 shadow-[0_4px_0_0_#059669]"
                    : "bg-white/90 hover:bg-white text-emerald-700 border-emerald-300 shadow-sm"
                }`}
              >
                {togglingReady
                  ? "Đang lưu..."
                  : myReady
                    ? "✓ ĐÃ SẴN SÀNG (HỦY)"
                    : "SẴN SÀNG"}
              </button>
            )}

            {/* Host Start Game Button with explicit disabled reasons */}
            {isHost &&
              (() => {
                const { allReady, unreadyCount } = readinessSummary(room);
                const hasCategories =
                  (room.selectedCategories || []).length > 0;
                const canStart =
                  room.players.length >= 2 && allReady && hasCategories;

                let disabledReason = "";
                if (room.players.length < 2) {
                  disabledReason =
                    "Cần ít nhất 2 người chơi để bắt đầu (tối thiểu 2 người)";
                } else if (!allReady) {
                  disabledReason =
                    unreadyCount === 1
                      ? "Đang chờ 1 người sẵn sàng"
                      : `Đang chờ ${unreadyCount} người sẵn sàng`;
                } else if (!hasCategories) {
                  disabledReason = "Vui lòng chọn ít nhất 1 chủ đề từ khóa";
                }

                return (
                  <div className="flex flex-col items-end gap-1">
                    <button
                      onClick={handleStartGame}
                      disabled={starting || !canStart}
                      title={canStart ? "" : disabledReason}
                      className="bouncy-btn px-6 py-2.5 bg-emerald-500 hover:bg-emerald-600 text-white font-black text-sm rounded-2xl shadow-[0_4px_0_0_#059669] transition-all disabled:opacity-50 flex items-center gap-1.5"
                    >
                      <span>🚀</span>
                      <span>
                        {starting ? "Đang bắt đầu..." : "BẮT ĐẦU GAME"}
                      </span>
                    </button>
                    {!canStart && (
                      <span className="text-[11px] font-bold text-amber-600 max-w-xs text-right">
                        {disabledReason}
                      </span>
                    )}
                  </div>
                );
              })()}
          </div>
        </div>

        {/* Room Settings: Selected Categories */}
        <section className="glass-panel-game p-4 shadow-lg">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
            <div>
              <h2 className="text-sm font-black text-slate-800">
                Chủ đề từ khóa
              </h2>
              <p className="text-[10px] text-slate-500 font-semibold mt-0.5">
                Từ trong các chủ đề đã chọn sẽ được xáo trộn ngẫu nhiên.
              </p>
            </div>
            {isHost && room.status === "WAITING" && (
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() =>
                    setCategoryDraft(
                      CATEGORY_OPTIONS.map((category) => category.id),
                    )
                  }
                  disabled={savingCategories}
                  className="rounded-xl px-3 py-1.5 text-[10px] font-extrabold bg-indigo-100 text-indigo-700 hover:bg-indigo-200 disabled:opacity-50 transition-colors"
                >
                  Chọn tất cả
                </button>
                <button
                  type="button"
                  onClick={() => setCategoryDraft([])}
                  disabled={savingCategories || categoryDraft.length === 0}
                  className="rounded-xl px-3 py-1.5 text-[10px] font-extrabold bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-50 transition-colors"
                >
                  Bỏ chọn tất cả
                </button>
              </div>
            )}
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {CATEGORY_OPTIONS.map((category) => {
              const selected = categoryDraft.includes(category.id);
              const editable =
                isHost && room.status === "WAITING" && !savingCategories;
              return (
                <button
                  key={category.id}
                  type="button"
                  aria-pressed={selected}
                  disabled={!editable}
                  onClick={() =>
                    setCategoryDraft((current) =>
                      selected
                        ? current.filter((id) => id !== category.id)
                        : [...current, category.id],
                    )
                  }
                  className={`rounded-2xl border px-3 py-2.5 text-left flex items-center gap-2 transition-all disabled:cursor-default ${
                    selected
                      ? "bg-indigo-600 text-white border-indigo-500 shadow-md"
                      : "bg-white/80 text-slate-700 border-slate-200 hover:border-indigo-300"
                  }`}
                >
                  <span>{category.icon}</span>
                  <span className="text-xs font-extrabold">
                    {category.label}
                  </span>
                  {selected && (
                    <span className="ml-auto text-xs font-black">✓</span>
                  )}
                </button>
              );
            })}
          </div>
          {isHost && room.status === "WAITING" && categoriesDirty && (
            <div className="mt-3 flex items-center justify-between gap-3">
              <span className="text-[10px] font-bold text-amber-700">
                {categoryDraft.length === 0
                  ? "Chọn ít nhất một chủ đề."
                  : "Thay đổi chưa được lưu."}
              </span>
              <button
                type="button"
                onClick={handleSaveCategories}
                disabled={savingCategories || categoryDraft.length === 0}
                className="rounded-xl px-4 py-2 text-xs font-black bg-emerald-500 hover:bg-emerald-600 text-white shadow-sm disabled:opacity-50 transition-all"
              >
                {savingCategories ? "Đang lưu..." : "Lưu chủ đề"}
              </button>
            </div>
          )}
        </section>

        {error && (
          <div className="p-3 bg-rose-500/20 border border-rose-500/40 text-rose-800 rounded-2xl text-xs font-bold flex items-center justify-between">
            <span>⚠️ {error}</span>
            <button
              onClick={() => setError(null)}
              className="text-rose-700 hover:text-rose-900 font-black ml-2"
            >
              ✕
            </button>
          </div>
        )}

        <PlayerList
          players={room.players}
          hostPlayerId={room.hostPlayerId}
          currentPlayerId={playerId}
          onKick={handleKick}
          canKick={isHost && room.status === "WAITING"}
        />
      </div>

      {/* Right Column: Chat Panel */}
      <div className="lg:col-span-1 h-[480px]">
        <ChatPanel roomId={room.roomId} />
      </div>
    </div>
  );
};
