import React, { useState } from "react";
import {
  Mic,
  MicOff,
  Volume2,
  VolumeX,
  Settings,
  AlertCircle,
  PowerOff,
} from "lucide-react";
import { useVoiceStore, voiceStore } from "../store/voiceStore";
import { voiceChatManager } from "../webrtc/VoiceChatManager";

interface VoiceControlsProps {
  isHost?: boolean;
  roomId?: string;
  className?: string;
}

export const VoiceControls: React.FC<VoiceControlsProps> = ({
  className = "",
}) => {
  const isMicOn = useVoiceStore((s) => s.isMicOn);
  const isMuted = useVoiceStore((s) => s.isMuted);
  const isDeafened = useVoiceStore((s) => s.isDeafened);
  const isLocalSpeaking = useVoiceStore((s) => s.isLocalSpeaking);
  const micError = useVoiceStore((s) => s.micError);
  const audioDevices = useVoiceStore((s) => s.audioDevices);
  const selectedDeviceId = useVoiceStore((s) => s.selectedDeviceId);

  const [showSettings, setShowSettings] = useState(false);
  const [masterVolume, setMasterVolume] = useState(0.75);

  const handleToggleMic = async () => {
    if (!isMicOn) {
      await voiceChatManager.startMic();
    } else {
      voiceChatManager.setMute(!isMuted);
    }
  };

  const handleStopMic = () => {
    voiceChatManager.stopMic();
  };

  const handleToggleDeafen = () => {
    voiceChatManager.setDeafen(!isDeafened);
  };

  const handleDeviceChange = async (e: React.ChangeEvent<HTMLSelectElement>) => {
    const deviceId = e.target.value;
    voiceStore.setSelectedDeviceId(deviceId);
    if (isMicOn) {
      voiceChatManager.stopMic();
      await voiceChatManager.startMic();
    }
  };

  return (
    <div
      className={`glass-panel-game p-1.5 px-2 flex flex-col gap-1.5 rounded-xl select-none ${className}`}
    >
      {/* Voice Action Bar */}
      <div className="flex items-center justify-between gap-1.5">
        {/* Left: Mic Controls */}
        <div className="flex items-center gap-1.5">
          {!isMicOn ? (
            <button
              type="button"
              onClick={handleToggleMic}
              className="flex items-center gap-1.5 bg-emerald-500 hover:bg-emerald-600 text-white font-black text-xs px-2.5 py-1.5 rounded-lg shadow-sm transition-all active:scale-95"
              title="Bật micro của bạn để trò chuyện"
            >
              <Mic className="w-3.5 h-3.5" />
              <span>Bật Mic</span>
            </button>
          ) : (
            <div className="flex items-center gap-1">
              {/* Mute/Unmute Toggle */}
              <button
                type="button"
                onClick={handleToggleMic}
                className={`flex items-center gap-1 text-xs font-black px-2.5 py-1.5 rounded-lg border transition-all shadow-sm active:scale-95 ${
                  isMuted
                    ? "bg-rose-100 text-rose-700 border-rose-300 hover:bg-rose-200"
                    : isLocalSpeaking
                    ? "bg-emerald-500 text-white border-emerald-600 ring-2 ring-emerald-300 animate-pulse"
                    : "bg-emerald-100 text-emerald-800 border-emerald-300 hover:bg-emerald-200"
                }`}
                title={isMuted ? "Bật lại mic (Unmute)" : "Tắt tiếng mic (Mute)"}
              >
                {isMuted ? (
                  <MicOff className="w-3.5 h-3.5" />
                ) : (
                  <Mic className="w-3.5 h-3.5" />
                )}
                <span>
                  {isMuted
                    ? "Tắt tiếng"
                    : isLocalSpeaking
                    ? "Đang nói..."
                    : "Mic Bật"}
                </span>
              </button>

              {/* Stop mic completely */}
              <button
                type="button"
                onClick={handleStopMic}
                className="p-1.5 bg-slate-100 hover:bg-rose-100 hover:text-rose-600 text-slate-500 rounded-lg border border-slate-200 transition-all active:scale-95"
                title="Tắt micro hoàn toàn"
              >
                <PowerOff className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          {/* Right/Middle: Listen / Deafen ("có nghe mic hay không") */}
          <button
            type="button"
            onClick={handleToggleDeafen}
            className={`flex items-center gap-1 text-xs font-bold px-2 py-1.5 rounded-lg border transition-all active:scale-95 ${
              isDeafened
                ? "bg-amber-100 text-amber-800 border-amber-300 hover:bg-amber-200"
                : "bg-slate-100 text-slate-700 border-slate-200 hover:bg-slate-200"
            }`}
            title={
              isDeafened
                ? "Bật lại âm thanh người khác"
                : "Tắt tiếng người khác (Không nghe mic)"
            }
          >
            {isDeafened ? (
              <>
                <VolumeX className="w-3.5 h-3.5 text-amber-700" />
                <span>Không nghe</span>
              </>
            ) : (
              <>
                <Volume2 className="w-3.5 h-3.5 text-slate-600" />
                <span>Nghe mic</span>
              </>
            )}
          </button>
        </div>

        {/* Right: Settings Toggle */}
        <button
          type="button"
          onClick={() => {
            setShowSettings(!showSettings);
            voiceChatManager.refreshAudioDevices();
          }}
          className={`p-1.5 rounded-lg border transition-all active:scale-95 ${
            showSettings
              ? "bg-sky-100 text-sky-700 border-sky-300"
              : "bg-slate-100 text-slate-500 border-slate-200 hover:bg-slate-200"
          }`}
          title="Cài đặt thiết bị micro"
        >
          <Settings className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Permission or error alert */}
      {micError && (
        <div className="flex items-center gap-1.5 bg-rose-50 border border-rose-200 p-1.5 rounded-lg text-[10px] text-rose-700 font-bold">
          <AlertCircle className="w-3.5 h-3.5 shrink-0 text-rose-500" />
          <span className="truncate">{micError}</span>
        </div>
      )}

      {/* Settings dropdown */}
      {showSettings && (
        <div className="pt-1.5 border-t border-slate-100 flex flex-col gap-2">
          {/* Master volume slider */}
          <div className="flex flex-col gap-0.5">
            <div className="flex items-center justify-between text-[10px] font-bold text-slate-600">
              <span>Âm lượng nghe:</span>
              <span className="text-emerald-600 font-extrabold">
                {Math.round(masterVolume * 100)}%
              </span>
            </div>
            <input
              type="range"
              min="0"
              max="1"
              step="0.05"
              value={masterVolume}
              onChange={(e) => {
                const val = parseFloat(e.target.value);
                setMasterVolume(val);
                voiceChatManager.setMasterVolume(val);
              }}
              className="w-full accent-emerald-500 h-1.5 bg-slate-200 rounded-lg cursor-pointer"
            />
          </div>

          {/* Device selector */}
          <div className="flex flex-col gap-0.5">
            <label className="text-[10px] font-bold text-slate-600">
              Chọn thiết bị micro:
            </label>
            <select
              value={selectedDeviceId || ""}
              onChange={handleDeviceChange}
              className="text-[11px] bg-slate-50 border border-slate-200 rounded-lg p-1 text-slate-700 focus:outline-none focus:border-sky-400"
            >
              <option value="">Thiết bị mặc định</option>
              {audioDevices.map((d, i) => (
                <option key={d.deviceId || i} value={d.deviceId}>
                  {d.label || `Microphone ${i + 1}`}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}
    </div>
  );
};
