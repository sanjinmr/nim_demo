package com.netease.nim.uikit.common.media.audioplayer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.text.TextUtils;

import com.netease.nim.uikit.R;
import com.netease.nim.uikit.common.util.log.LogUtil;
import com.netease.nimlib.sdk.media.player.AudioPlayer;
import com.netease.nimlib.sdk.media.player.OnPlayListener;

abstract public class BaseAudioControl<T> {
	
	interface AudioControllerState {
		int stop = 0;
		int ready = 1;
		int playing = 2;
	}
	
	private int state;
	protected boolean isEarPhoneModeEnable = true; // 是否是听筒模式

	public interface AudioControlListener {
		//AudioControl准备就绪，已经postDelayed playRunnable，不等同于AudioPlayer已经开始播放
		public void onAudioControllerReady(Playable playable);

		/**
		 * 结束播放
		 */
		public void onEndPlay(Playable playable);

		/**
		 * 显示播放过程中的进度条
		 * @param curPosition 当前进度，如果传-1则自动获取进度
		 */
		public void updatePlayingProgress(Playable playable, long curPosition);
	}
	
	protected AudioControlListener audioControlListener;

	protected Context mContext;
	protected AudioPlayer currentAudioPlayer;
	protected Playable currentPlayable;
	
	protected boolean needSeek = false;
	protected long seekPosition;

    private MediaPlayer mSuffixPlayer = null;
    private boolean mSuffix = false;
    protected Handler mHandler = new Handler();

    private BasePlayerListener basePlayerListener = null;

    protected void setOnPlayListener(Playable playingPlayable, AudioControlListener audioControlListener) {
        this.audioControlListener = audioControlListener;

        basePlayerListener = new BasePlayerListener(currentAudioPlayer, playingPlayable);
        currentAudioPlayer.setOnPlayListener(basePlayerListener);
        basePlayerListener.setAudioControlListener(audioControlListener);
    }

    public void setEarPhoneModeEnable(boolean isEarPhoneModeEnable) {
    	this.isEarPhoneModeEnable = isEarPhoneModeEnable;
		if (isEarPhoneModeEnable) {
			updateAudioStreamType(AudioManager.STREAM_VOICE_CALL);
		} else {
			updateAudioStreamType(AudioManager.STREAM_MUSIC);
		}
    }
    
	@SuppressWarnings("unchecked")
	public void changeAudioControlListener(AudioControlListener audioControlListener) {
		this.audioControlListener = audioControlListener;
	
		if (isPlayingAudio()) {
			OnPlayListener onPlayListener = currentAudioPlayer.getOnPlayListener();
			if (onPlayListener != null) {
				((BasePlayerListener) onPlayListener).setAudioControlListener(audioControlListener);
			}
		}
	}
	
	public AudioControlListener getAudioControlListener() {
		return audioControlListener;
	}
	
	public BaseAudioControl(Context context, boolean suffix) {
		this.mContext = context;
        this.mSuffix = suffix;
	}

    protected void playSuffix() {
        if(mSuffix) {
            mSuffixPlayer = MediaPlayer.create(mContext, R.raw.audio_end_tip);
            mSuffixPlayer.setLooping(false);
            mSuffixPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mSuffixPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mSuffixPlayer.release();
                    mSuffixPlayer = null;
                }
            });
            mSuffixPlayer.start();
        }
    }

	protected boolean startAudio(
			Playable playable,
			AudioControlListener audioControlListener, 
			int audioStreamType, 
			boolean resetOrigAudioStreamType,
			long delayMillis) 
	{
        String filePath = playable.getPath();
        if(TextUtils.isEmpty(filePath)) {
            return false;
        }

		//正在播放，停止播放
		if(isPlayingAudio()) {
			stopAudio();
			//如果相等，就是同一个对象了
			if(currentPlayable.isAudioEqual(playable)) {
				return false;
			}
		}					

		state = AudioControllerState.stop;
		
		currentPlayable = playable;
		// 构造播放器对象
		currentAudioPlayer = new AudioPlayer(mContext);
        currentAudioPlayer.setDataSource(filePath);

		setOnPlayListener(currentPlayable, audioControlListener);
				
		if (resetOrigAudioStreamType) {
			this.origAudioStreamType = audioStreamType;			
		}
		this.currentAudioStreamType = audioStreamType;
		
		mHandler.postDelayed(playRunnable, delayMillis);

		state = AudioControllerState.ready;
		if (audioControlListener != null) {
			audioControlListener.onAudioControllerReady(currentPlayable);
		}
		
		return true;	
	}
	
	Runnable playRunnable = new Runnable() {
		
		@Override
		public void run() {
			if (currentAudioPlayer == null) {
				LogUtil.audio("playRunnable run when currentAudioPlayer == null");
				return;
			}
			// 开始播放。需要传入一个 Stream Type 参数，表示是用听筒播放还是扬声器。取值可参见
			// android.media.AudioManager#STREAM_***
			// AudioManager.STREAM_VOICE_CALL 表示使用听筒模式
			// AudioManager.STREAM_MUSIC 表示使用扬声器模式
			currentAudioPlayer.start(currentAudioStreamType);
		}
	};
	
	private int origAudioStreamType;
	private int currentAudioStreamType;
	public int getCurrentAudioStreamType() {
		return currentAudioStreamType;
	}
	
	protected int getUserSettingAudioStreamType() {
		// 听筒模式/扬声器模式
		if (isEarPhoneModeEnable) {
			return AudioManager.STREAM_VOICE_CALL;
		} else {
			return AudioManager.STREAM_MUSIC;
		}
	}
	
	protected void resetAudioController(Playable playable) {
        currentAudioPlayer.setOnPlayListener(null);
		currentAudioPlayer = null;
		
		state = AudioControllerState.stop;
	}
	
	//playing or ready
	public boolean isPlayingAudio() {
		if(currentAudioPlayer != null) {
			return state == AudioControllerState.playing
                    || state == AudioControllerState.ready;
		} else {
			return false;
		}	
	}
	
	//stop or cancel
	public void stopAudio() {
        if (state == AudioControllerState.playing) {
            //playing->stop
			// 主动停止播放
            currentAudioPlayer.stop();
        } else if (state == AudioControllerState.ready) {
            //ready->cancel
            mHandler.removeCallbacks(playRunnable);
            resetAudioController(currentPlayable);

            if (audioControlListener != null) {
                audioControlListener.onEndPlay(currentPlayable);
            }
        }
	}
	
	public boolean updateAudioStreamType(int audioStreamType) {
		if(!isPlayingAudio()) {
			return false;
		}
			
		if (audioStreamType == getCurrentAudioStreamType()) {
			return false;
		}
				
		changeAudioStreamType(audioStreamType);
		return true;
	}
	
	public boolean restoreAudioStreamType() {
		if(!isPlayingAudio()) {
			return false;
		}
		
		if (origAudioStreamType == getCurrentAudioStreamType()) {
			return false;
		}

        changeAudioStreamType(origAudioStreamType);
        return true;
	}
	
	private void changeAudioStreamType(int audioStreamType) {		
		if (currentAudioPlayer.isPlaying()) {
			seekPosition = currentAudioPlayer.getCurrentPosition();
			needSeek = true;
			currentAudioStreamType = audioStreamType;
			currentAudioPlayer.start(audioStreamType);
		} else {
			currentAudioStreamType = origAudioStreamType;
		}	
	}

	/**
	 * 回放
	 网易云通信的语音消息格式有 aac 和 amr 两种格式可选，由于 2.x 系统的原生 MediaPlayer 不支持 aac 格式，
	 因此 SDK 也提供了一个 AudioPlayer 来播放网易云通信的语音消息。
	 同时，将 MediaPlayer 的接口进行了一些封装，使得在会话场景下播放语音更加方便。 使用示例代码如下

	 定义一个播放进程回调类
	 */
	public class BasePlayerListener implements OnPlayListener {
		protected AudioPlayer listenerPlayingAudioPlayer;
		protected Playable listenerPlayingPlayable;
		protected AudioControlListener audioControlListener;
		
		public BasePlayerListener(AudioPlayer playingAudioPlayer, Playable playingPlayable) {
			listenerPlayingAudioPlayer = playingAudioPlayer;
			listenerPlayingPlayable = playingPlayable;
		}
		
		public void setAudioControlListener(AudioControlListener audioControlListener) {
			this.audioControlListener = audioControlListener;
		}
		
		protected boolean checkAudioPlayerValid() {
			if (currentAudioPlayer != listenerPlayingAudioPlayer) {
				return false;
			}
			
			return true;
		}

		/**
		 * 音频转码解码完成，会马上开始播放了
		 */
		@Override
		public void onPrepared() {
			if (!checkAudioPlayerValid()) {
				return;
			}

            state = AudioControllerState.playing;
			if (needSeek) {
				needSeek = false;
				// 如果中途切换播放设备，重新调用 start，传入指定的 streamType 即可。player 会自动停止播放，然后再以新的 streamType 重新开始播放。
				// 如果需要从中断的地方继续播放，需要外面自己记住已经播放过的位置，然后在 onPrepared 回调中调用 seekTo
				listenerPlayingAudioPlayer.seekTo((int) seekPosition);
			}
		}

		/**
		 * 播放进度报告，每隔 500ms 会回调一次，告诉当前进度。 参数为当前进度，单位为毫秒，可用于更新 UI
		 * @param curPosition
         */
		@Override
		public void onPlaying(long curPosition) {
			if (!checkAudioPlayerValid()) {
				return;
			}
			
			if (audioControlListener != null) {
				audioControlListener.updatePlayingProgress(listenerPlayingPlayable, curPosition);
			}
		}

		/**
		 * 播放被中断了
		 */
		@Override
		public void onInterrupt() {
			if (!checkAudioPlayerValid()) {
				return;
			}
			
			resetAudioController(listenerPlayingPlayable);
            if (audioControlListener != null) {
                audioControlListener.onEndPlay(currentPlayable);
            }

		}

		/**
		 * 播放过程中出错。参数为出错原因描述
		 * @param error
         */
		@Override
		public void onError(String error) {
			if (!checkAudioPlayerValid()) {
				return;
			}
			
			resetAudioController(listenerPlayingPlayable);
            if (audioControlListener != null) {
                audioControlListener.onEndPlay(currentPlayable);
            }
		}

		/**
		 * 播放结束
		 */
		@Override
		public void onCompletion() {
			if (!checkAudioPlayerValid()) {
				return;
			}
			
			resetAudioController(listenerPlayingPlayable);
            if (audioControlListener != null) {
                audioControlListener.onEndPlay(currentPlayable);
            }

            playSuffix();
		}
	};

	public void startPlayAudio(
			T t,
			AudioControlListener audioControlListener) {
		startPlayAudio(t, audioControlListener, getUserSettingAudioStreamType());
	}

	public void startPlayAudio(
			T t,
			AudioControlListener audioControlListener,
			int audioStreamType) {
		startPlayAudioDelay(0, t, audioControlListener, audioStreamType);		
	}
	
	public void startPlayAudioDelay(long delayMillis, T t, AudioControlListener audioControlListener) {		
		startPlayAudioDelay(delayMillis, t, audioControlListener, getUserSettingAudioStreamType());	
	}
	
	public abstract void startPlayAudioDelay(long delayMillis, T t, AudioControlListener audioControlListener, int audioStreamType);
	public abstract T getPlayingAudio();
}
