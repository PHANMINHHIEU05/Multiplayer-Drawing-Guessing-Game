import React, { useState, useCallback } from 'react';
import { usePlayerStore, playerStore } from '../store/playerStore';
import { CreateRoomForm } from '../features/room/CreateRoomForm';
import { JoinRoomForm } from '../features/room/JoinRoomForm';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { PaintSplashOverlay } from '../components/PaintSplashOverlay';

const AVATAR_SEEDS = ['Dopamine', 'Felix', 'Luna', 'Oscar', 'Milo', 'Coco', 'Pepper', 'Simba', 'Gizmo'];

export const HomePage: React.FC = () => {
  const { username } = usePlayerStore((s) => s);
  const [inputName, setInputName] = useState(username);
  const [activeTab, setActiveTab] = useState<'create' | 'join'>('create');
  const [avatarIndex, setAvatarIndex] = useState(0);
  const [replayIntro, setReplayIntro] = useState(false);
  const [hasRevealed, setHasRevealed] = useState(false);
  const [showGlobalChat, setShowGlobalChat] = useState(false);
  const [chatInput, setChatInput] = useState('');
  const [chatMessages, setChatMessages] = useState<{ sender: string; text: string; color: string }[]>([
    { sender: 'HọaSĩPro', text: 'Ai solo vẽ không? 😎', color: 'text-primary' },
    { sender: 'NoName99', text: 'Chờ xíu đang vào phòng nè!', color: 'text-secondary' },
  ]);

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
    setChatMessages((prev) => [
      ...prev,
      { sender: inputName || 'Bạn', text: chatInput.trim(), color: 'text-emerald-600' },
    ]);
    setChatInput('');
  };

  const handleIntroComplete = useCallback(() => {
    setHasRevealed(true);
    setReplayIntro(false);
  }, []);

  const avatarUrl = `https://api.dicebear.com/7.x/bottts/svg?seed=${AVATAR_SEEDS[avatarIndex]}`;

  return (
    <>
      <PaintSplashOverlay
        forcePlay={replayIntro}
        onComplete={handleIntroComplete}
      />

      <div className="min-h-screen flex flex-col justify-between p-4 md:p-6 text-slate-800 overflow-hidden">
        {/* Top Header Bar */}
        <header className={`max-w-6xl w-full mx-auto flex items-center justify-between py-2 transition-all duration-700 ${
          hasRevealed ? 'animate-pop-in' : 'opacity-0'
        }`}>
          <div className="flex items-center gap-3">
            <span className="text-3xl animate-bounce">🎨</span>
            <span className="text-2xl font-black bubbly-logo text-white drop-shadow-md">
              Dopamine
            </span>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <button
              onClick={() => {
                setHasRevealed(false);
                setReplayIntro(true);
              }}
              title="Xem lại hiệu ứng cọ vẽ đổ sơn"
              className="bg-amber-400 hover:bg-amber-300 text-slate-950 font-black text-xs px-3 py-2 rounded-2xl transition-all shadow-md flex items-center gap-1.5 hover:scale-105"
            >
              <span>🎬</span>
              <span className="hidden sm:inline">Xem Intro Cọ Vẽ</span>
            </button>
            <button className="bg-white/20 hover:bg-white/30 text-white p-2 sm:p-2.5 rounded-2xl backdrop-blur-md transition-all shadow-md">
              <span className="material-symbols-outlined text-lg sm:text-xl">volume_up</span>
            </button>
            <button className="bg-white/20 hover:bg-white/30 text-white p-2 sm:p-2.5 rounded-2xl backdrop-blur-md transition-all shadow-md">
              <span className="material-symbols-outlined text-lg sm:text-xl">help</span>
            </button>
            <ConnectionStatus />
          </div>
        </header>

        {/* Main Content Area */}
        <main className={`max-w-5xl w-full mx-auto my-4 flex-1 flex flex-col items-center justify-center gap-6 transition-all duration-700 ${
          hasRevealed ? 'animate-pop-in' : 'opacity-0 scale-90'
        }`}>
          {/* 3D Bubbly Logo Centerpiece */}
          <div className="text-center group select-none">
            <div className="inline-block relative">
              <h2 className="text-5xl sm:text-7xl md:text-8xl bubbly-logo transform group-hover:scale-105 transition-transform duration-300">
                Dopamine<span className="text-sky-300">.io</span>
              </h2>
              <div className="absolute -top-3 -right-6 text-3xl rotate-12">🖌️</div>
            </div>
            <p className="text-white font-bold text-sm sm:text-base md:text-lg drop-shadow-md mt-1">
              Game Vẽ & Đoán Từ Đầy Phấn Khích Cùng Bạn Bè! ⚡
            </p>
          </div>

          {/* Main Dual Cards Grid */}
          <div className="w-full grid grid-cols-1 md:grid-cols-12 gap-5 items-stretch">
            {/* Left: Player Profile & Avatar */}
            <div className="md:col-span-4 glass-panel rounded-3xl p-5 flex flex-col items-center justify-between shadow-2xl relative">
              <div className="w-full text-center">
                <span className="text-[11px] uppercase font-extrabold tracking-wider text-sky-700 bg-sky-100 px-3 py-1 rounded-full">
                  Hồ Sơ Của Bạn
                </span>

                {/* Avatar Preview with Refresh Button */}
                <div className="relative w-24 h-24 sm:w-28 sm:h-28 mx-auto my-4 cursor-pointer group" onClick={cycleAvatar}>
                  <img
                    src={avatarUrl}
                    alt="Avatar"
                    className="w-full h-full rounded-full border-4 border-white shadow-xl bg-amber-100 object-cover transform group-hover:scale-105 transition-transform"
                  />
                  <button
                    type="button"
                    title="Đổi avatar ngẫu nhiên"
                    className="absolute bottom-0 right-0 bg-secondary hover:bg-secondary-dark text-white p-2 rounded-full shadow-lg hover:scale-110 transition-transform"
                  >
                    <span className="material-symbols-outlined text-sm">refresh</span>
                  </button>
                </div>

                {/* Nickname Input */}
                <div className="space-y-1">
                  <label className="text-[10px] font-black uppercase tracking-wider text-slate-500 block">
                    Biệt danh của bạn
                  </label>
                  <input
                    type="text"
                    placeholder="Nhập tên của bạn..."
                    value={inputName}
                    onChange={(e) => {
                      setInputName(e.target.value);
                      playerStore.setPlayer(e.target.value);
                    }}
                    onBlur={handleNameBlur}
                    className="w-full text-center font-black text-base bg-white/90 border-2 border-sky-300 focus:border-primary focus:ring-4 focus:ring-sky-200 rounded-2xl py-2.5 px-3 outline-none transition-all shadow-inner text-slate-800"
                  />
                </div>
              </div>

              {/* Stats Bar */}
              <div className="w-full bg-sky-50/90 rounded-2xl p-2.5 mt-4 border border-sky-100 flex items-center justify-between text-xs font-extrabold text-slate-600">
                <span>🏆 Cấp độ: <strong className="text-primary">Lv. 15</strong></span>
                <span className="text-amber-500">⭐ 1,420 pts</span>
              </div>
            </div>

            {/* Right: Create / Join Room Tabs & Forms */}
            <div className="md:col-span-8 glass-panel rounded-3xl p-5 shadow-2xl flex flex-col justify-between">
              {/* Tab Buttons */}
              <div className="flex p-1.5 gap-2 bg-sky-100/90 rounded-2xl mb-4">
                <button
                  type="button"
                  onClick={() => setActiveTab('create')}
                  className={`flex-1 py-2.5 rounded-xl font-black text-xs sm:text-sm transition-all ${
                    activeTab === 'create'
                      ? 'text-white bg-primary shadow-md scale-[1.02]'
                      : 'text-slate-600 hover:bg-white/60'
                  }`}
                >
                  ➕ TẠO PHÒNG MỚI
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('join')}
                  className={`flex-1 py-2.5 rounded-xl font-black text-xs sm:text-sm transition-all ${
                    activeTab === 'join'
                      ? 'text-white bg-primary shadow-md scale-[1.02]'
                      : 'text-slate-600 hover:bg-white/60'
                  }`}
                >
                  🚪 VÀO PHÒNG
                </button>
              </div>

              {/* Form Content */}
              {activeTab === 'create' ? <CreateRoomForm /> : <JoinRoomForm />}
            </div>
          </div>
        </main>

        {/* Footer */}
        <footer className={`text-center text-xs font-bold text-white/80 py-2 drop-shadow transition-opacity duration-700 ${
          hasRevealed ? 'opacity-100' : 'opacity-0'
        }`}>
          Dopamine Multiplayer Drawing & Guessing Game • Real-time Canvas Engine
        </footer>
      </div>

      {/* Floating Global Chat Panel */}
      <div className={`fixed bottom-4 right-4 z-40 flex flex-col items-end transition-all duration-700 ${
        hasRevealed ? 'opacity-100 scale-100' : 'opacity-0 scale-75 pointer-events-none'
      }`}>
        {showGlobalChat && (
          <div className="glass-panel w-72 sm:w-80 rounded-2xl mb-3 overflow-hidden shadow-2xl border-2 border-white flex flex-col animate-slideUp">
            <div className="bg-primary p-3 flex justify-between items-center text-white">
              <span className="font-extrabold text-xs flex items-center gap-1.5">
                <span className="material-symbols-outlined text-base">chat</span>
                Chat Phòng Chờ
              </span>
              <button
                onClick={() => setShowGlobalChat(false)}
                className="hover:bg-white/20 rounded p-0.5 transition-colors"
              >
                <span className="material-symbols-outlined text-sm">close</span>
              </button>
            </div>
            <div className="h-44 p-3 overflow-y-auto space-y-2 bg-white/70 text-xs custom-scrollbar">
              {chatMessages.map((msg, i) => (
                <div key={i}>
                  <strong className={msg.color}>{msg.sender}: </strong>
                  <span className="text-slate-700">{msg.text}</span>
                </div>
              ))}
            </div>
            <form onSubmit={handleSendChat} className="p-2 bg-white/90 border-t border-sky-100 flex gap-1.5">
              <input
                type="text"
                placeholder="Nhắn tin..."
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                className="flex-1 bg-sky-50 rounded-xl px-3 py-1.5 text-xs text-slate-800 outline-none border border-sky-200 focus:border-primary"
              />
              <button
                type="submit"
                className="bg-primary hover:bg-primary-dark text-white px-3 py-1.5 rounded-xl font-bold text-xs"
              >
                Gửi
              </button>
            </form>
          </div>
        )}

        <button
          onClick={() => setShowGlobalChat(!showGlobalChat)}
          className="bouncy-btn w-12 h-12 bg-secondary hover:bg-secondary-dark text-white rounded-full shadow-[0_4px_12px_rgba(124,58,237,0.4)] flex items-center justify-center transition-all"
          title="Mở chat phòng chờ"
        >
          <span className="material-symbols-outlined text-xl">chat</span>
        </button>
      </div>
    </>
  );
};

