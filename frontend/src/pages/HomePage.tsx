import React, { useState, useCallback, useEffect, useRef } from "react";
import { usePlayerStore, playerStore } from "../store/playerStore";
import { useLobbyChatStore } from "../store/lobbyChatStore";
import { wsClient } from "../websocket/WebSocketClient";
import { MessageType } from "../websocket/protocol";
import { CreateRoomForm } from "../features/room/CreateRoomForm";
import { JoinRoomForm } from "../features/room/JoinRoomForm";
import { ConnectionStatus } from "../components/ConnectionStatus";
import { PaintSplashOverlay } from "../components/PaintSplashOverlay";
import { SoundToggle } from "../components/SoundToggle";
import { audioManager } from "../audio/AudioManager";

const AVATAR_SEEDS = [
  "Dopamine",
  "Felix",
  "Luna",
  "Oscar",
  "Milo",
  "Coco",
  "Pepper",
  "Simba",
  "Gizmo",
];

export const HomePage: React.FC = () => {
  const { username, playerId } = usePlayerStore((s) => s);
  const [inputName, setInputName] = useState(username);
  const [activeTab, setActiveTab] = useState<"create" | "join">("create");
  const [avatarIndex, setAvatarIndex] = useState(0);
  const [replayIntro, setReplayIntro] = useState(false);
  const [hasRevealed, setHasRevealed] = useState(false);
  const [showGlobalChat, setShowGlobalChat] = useState(false);
  const [chatInput, setChatInput] = useState("");
  const chatBottomRef = useRef<HTMLDivElement | null>(null);

  const lobbyMessages = useLobbyChatStore((s) => s.messages);

  useEffect(() => {
    wsClient.send(MessageType.GET_LOBBY_CHAT, { limit: 50 }).catch(() => {});
  }, []);

  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [lobbyMessages, showGlobalChat]);

  const handleNameBlur = () => {
    if (inputName.trim()) {
      playerStore.setPlayer(inputName.trim());
    }
  };

  const cycleAvatar = () => {
    setAvatarIndex((prev) => (prev + 1) % AVATAR_SEEDS.length);
  };

  const handleSendChat = (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim()) return;

    wsClient
      .send(MessageType.SEND_LOBBY_CHAT, {
        playerId,
        username: inputName,
        content: chatInput.trim(),
      })
      .catch((err) => console.error("Lobby chat failed:", err));

    setChatInput("");
  };

  const handleIntroComplete = useCallback(() => {
    setHasRevealed(true);
    setReplayIntro(false);
    audioManager.playBGM("lobby");
  }, []);

  useEffect(() => {
    if (hasRevealed && !replayIntro) {
      audioManager.playBGM("lobby");
    }
  }, [hasRevealed, replayIntro]);

  const avatarUrl = `https://api.dicebear.com/7.x/bottts/svg?seed=${AVATAR_SEEDS[avatarIndex]}`;

  return (
    <>
      <PaintSplashOverlay
        forcePlay={replayIntro}
        onComplete={handleIntroComplete}
      />

      <div className="dg-page min-h-screen flex flex-col justify-between py-3 sm:py-4 text-slate-800">
        <header
          className={`dg-topbar flex items-center justify-between gap-3 transition-all duration-700 ${
            hasRevealed ? "animate-pop-in" : "opacity-0"
          }`}
        >
          <div className="dg-brand">
            <span className="dg-brand-palette">🎨</span>
            <span className="dg-brand-name text-2xl sm:text-3xl">
              Dopamine<span className="dg-brand-dot">.io</span>
            </span>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <button
              onClick={() => {
                audioManager.stopBGM();
                setHasRevealed(false);
                setReplayIntro(true);
              }}
              title="Xem lại hiệu ứng cọ vẽ đổ sơn"
              className="dg-yellow-button px-3 py-2 text-xs flex items-center gap-1.5"
            >
              <span>🎬</span>
              <span className="hidden sm:inline">Xem intro</span>
            </button>
            <SoundToggle />
            <button className="dg-icon-btn" title="Trợ giúp">
              <span className="material-symbols-outlined text-lg sm:text-xl">
                help
              </span>
            </button>
            <ConnectionStatus />
          </div>
        </header>

        <main
          className={`dg-home-stage flex-1 flex flex-col items-center justify-center gap-6 py-8 sm:py-10 transition-all duration-700 ${
            hasRevealed ? "animate-pop-in" : "opacity-0 scale-90"
          }`}
        >
          <div className="text-center group select-none">
            <div className="inline-block relative">
              <h1 className="dg-home-logo dg-brand-name transform group-hover:scale-[1.025] transition-transform duration-300">
                Dopamine<span className="dg-brand-dot">.io</span>
              </h1>
              <span
                className="absolute -top-4 -right-5 sm:-right-8 text-3xl sm:text-4xl rotate-12 drop-shadow-md"
                aria-hidden="true"
              >
                🖌️
              </span>
              <span
                className="absolute -bottom-2 -left-5 text-2xl -rotate-12 drop-shadow-md"
                aria-hidden="true"
              >
                ✨
              </span>
            </div>
            <p className="text-white font-extrabold text-sm sm:text-base drop-shadow-[0_2px_0_#15375f] mt-3">
              Vẽ nhanh · Đoán chuẩn · Cười hết cỡ!
            </p>
          </div>

          <section className="dg-home-card glass-panel w-full">
            <div className="grid grid-cols-1 md:grid-cols-12 gap-4 items-stretch">
              <aside className="dg-profile-panel md:col-span-4 p-4 sm:p-5 flex flex-col items-center justify-center text-center">
                <span className="text-[10px] uppercase font-black tracking-[.16em] text-sky-700">
                  Hồ sơ người chơi
                </span>

                <button
                  type="button"
                  className="relative w-24 h-24 sm:w-28 sm:h-28 mx-auto my-4 group"
                  onClick={cycleAvatar}
                  title="Đổi avatar ngẫu nhiên"
                >
                  <img
                    src={avatarUrl}
                    alt="Avatar của bạn"
                    className="w-full h-full rounded-full border-[3px] border-[#15375f] shadow-[0_4px_0_rgba(21,55,95,.3)] bg-amber-100 object-cover transform group-hover:scale-105 transition-transform"
                  />
                  <span className="absolute bottom-0 right-0 grid place-items-center w-8 h-8 bg-[#43c7ee] text-[#15375f] border-2 border-[#15375f] rounded-full shadow-sm">
                    <span className="material-symbols-outlined text-base">
                      refresh
                    </span>
                  </span>
                </button>

                <label className="w-full space-y-1.5">
                  <span className="text-[10px] font-black uppercase tracking-wider text-slate-500 block">
                    Biệt danh của bạn
                  </span>
                  <input
                    type="text"
                    placeholder="Nhập tên của bạn..."
                    value={inputName}
                    onChange={(e) => {
                      setInputName(e.target.value);
                      playerStore.setPlayer(e.target.value);
                    }}
                    onBlur={handleNameBlur}
                    className="w-full text-center font-black text-base bg-white border-2 border-[#15375f] focus:border-sky-500 rounded-xl py-2.5 px-3 outline-none transition-all shadow-inner text-slate-800"
                  />
                </label>
                <p className="mt-3 text-[10px] leading-relaxed font-semibold text-slate-500">
                  Chạm vào avatar để đổi nhân vật của bạn.
                </p>
              </aside>

              <div className="dg-form-panel md:col-span-8 p-3 sm:p-4 flex flex-col">
                <div
                  className="dg-form-tablist mb-4"
                  role="tablist"
                  aria-label="Chọn cách chơi"
                >
                  <button
                    type="button"
                    role="tab"
                    aria-selected={activeTab === "create"}
                    onClick={() => setActiveTab("create")}
                    className={`dg-form-tab ${activeTab === "create" ? "is-active" : ""}`}
                  >
                    ➕ TẠO PHÒNG
                  </button>
                  <button
                    type="button"
                    role="tab"
                    aria-selected={activeTab === "join"}
                    onClick={() => setActiveTab("join")}
                    className={`dg-form-tab ${activeTab === "join" ? "is-active" : ""}`}
                  >
                    🚪 VÀO PHÒNG
                  </button>
                </div>

                <div className="flex-1">
                  {activeTab === "create" ? (
                    <CreateRoomForm />
                  ) : (
                    <JoinRoomForm />
                  )}
                </div>
              </div>
            </div>
          </section>
        </main>

        <footer
          className={`text-center text-[10px] sm:text-xs font-bold text-white/90 px-4 py-2 drop-shadow transition-opacity duration-700 ${
            hasRevealed ? "opacity-100" : "opacity-0"
          }`}
        >
          Dopamine.io · Vẽ và đoán từ cùng bạn bè theo thời gian thực
        </footer>
      </div>

      <div
        className={`fixed bottom-4 right-4 z-40 flex flex-col items-end transition-all duration-700 ${
          hasRevealed
            ? "opacity-100 scale-100"
            : "opacity-0 scale-75 pointer-events-none"
        }`}
      >
        {showGlobalChat && (
          <div className="glass-panel w-72 sm:w-80 rounded-2xl mb-3 overflow-hidden flex flex-col animate-slideUp">
            <div className="dg-panel-heading">
              <span className="flex items-center gap-1.5">
                <span>💬</span>
                Chat sảnh chờ
              </span>
              <button
                onClick={() => setShowGlobalChat(false)}
                className="hover:bg-white/15 rounded-md p-0.5 transition-colors"
                aria-label="Đóng chat sảnh chờ"
              >
                <span className="material-symbols-outlined text-sm">close</span>
              </button>
            </div>
            <div className="h-44 p-3 overflow-y-auto space-y-2 bg-[#edf8fd] text-xs custom-scrollbar">
              {lobbyMessages.length === 0 && (
                <p className="py-8 text-center text-slate-400 font-semibold italic">
                  Chưa có tin nhắn trong sảnh.
                </p>
              )}
              {lobbyMessages.map((msg, i) => {
                const isCurrent = msg.playerId === playerId;
                return (
                  <div key={msg.messageId || i}>
                    <strong
                      className={
                        isCurrent ? "text-emerald-600" : "text-sky-700"
                      }
                    >
                      {msg.username || "Người chơi"}:{" "}
                    </strong>
                    <span className="text-slate-700">{msg.content}</span>
                  </div>
                );
              })}
              <div ref={chatBottomRef} />
            </div>
            <form
              onSubmit={handleSendChat}
              className="p-2 bg-white border-t-2 border-[#b5dbea] flex gap-1.5"
            >
              <input
                type="text"
                placeholder="Nhắn tin..."
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                className="flex-1 bg-sky-50 rounded-lg px-3 py-2 text-xs text-slate-800 outline-none border-2 border-sky-200 focus:border-sky-500"
              />
              <button
                type="submit"
                className="dg-primary-button px-3 py-1.5 text-xs"
              >
                Gửi
              </button>
            </form>
          </div>
        )}

        <button
          onClick={() => setShowGlobalChat(!showGlobalChat)}
          className="dg-primary-button bouncy-btn w-12 h-12 rounded-full flex items-center justify-center"
          title="Mở chat sảnh chờ"
          aria-expanded={showGlobalChat}
        >
          <span className="material-symbols-outlined text-xl">chat</span>
        </button>
      </div>
    </>
  );
};
