package com.example.localsocketmediaproxy;

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

public class MainActivity extends Activity implements SurfaceTextureListener {
	private TextureView videoView;
	private MediaPlayer mediaPlayer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		videoView = (TextureView) findViewById(R.id.videoview);
		videoView.setSurfaceTextureListener(this);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Intent intent = new Intent("start.socket.server");
		intent.setPackage(getPackageName());
		startService(intent);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}


	@Override
	public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width,
			int height) {
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			
			@Override
			public void run() {
				play(surface);
			}
		}, 2000);
	}
	
	private void play(SurfaceTexture surface) {
		if (null == mediaPlayer) mediaPlayer = new MediaPlayer();
		if (mediaPlayer.isPlaying()) mediaPlayer.stop();
		try {
			mediaPlayer.setDataSource("http://127.0.0.1:8888");
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		mediaPlayer.setSurface(new Surface(surface));
		try {
			mediaPlayer.prepare();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		mediaPlayer.start();
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
			int height) {
		
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		if (null != mediaPlayer && mediaPlayer.isPlaying()) mediaPlayer.stop();
		return true;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		
	}
}
