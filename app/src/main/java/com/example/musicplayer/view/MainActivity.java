package com.example.musicplayer.view;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.musicplayer.R;
import com.example.musicplayer.entiy.Song;
import com.example.musicplayer.service.PlayerService;
import com.example.musicplayer.util.FileHelper;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private boolean isChange; //拖动进度条
    private boolean isSeek;//标记是否在暂停的时候拖动进度条
    private boolean flag; //用做暂停的标记
    private int time;   //记录暂停的时间
    private Button mPlayerBtn;
    private ImageView mNextIv;
    private TextView mSongNameTv;
    private TextView mSingerTv;
    private Song mSong;
    private LinearLayout mLinear;
    private MediaPlayer mMediaPlayer;
    private SeekBar mSeekBar;
    private Thread mSeekBarThread;
    private PlayerService.PlayStatusBinder mPlayStatusBinder;
    //注册广播
    private IntentFilter intentFilter;
    private SongChangeReceiver songChangeReceiver;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mPlayStatusBinder = (PlayerService.PlayStatusBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {


        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        intentFilter = new IntentFilter();
        intentFilter.addAction("android.song.change");
        songChangeReceiver = new SongChangeReceiver();
        registerReceiver(songChangeReceiver, intentFilter);
        initView();
        onClick();
    }

    private void initView() {
        mSong = FileHelper.getSong();
        mSongNameTv = findViewById(R.id.tv_song_name);
        mSingerTv = findViewById(R.id.tv_singer);
        mLinear = findViewById(R.id.linear_player);
        mSeekBar = findViewById(R.id.sb_progress);
        mNextIv = findViewById(R.id.song_next);


        if (mSong.getTitle() != null) {
            //启动服务
            Intent playIntent = new Intent(MainActivity.this, PlayerService.class);
            bindService(playIntent, connection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "------initView:bindService ");

            mLinear.setVisibility(View.VISIBLE);
            mSongNameTv.setText(mSong.getTitle());
            mSingerTv.setText(mSong.getArtist());
            mSeekBar.setMax((int) mSong.getDuration());
            mSeekBar.setProgress((int) mSong.getCurrentTime());

        } else {
            mLinear.setVisibility(View.GONE);
        }
        mPlayerBtn = findViewById(R.id.btn_player);

        addMainFragment();
    }

    private void onClick() {
        //进度条的监听事件
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //防止在拖动进度条进行进度设置时与Thread更新播放进度条冲突
                isChange = true;
                isSeek = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.seekTo(seekBar.getProgress());
                } else {
                    time = seekBar.getProgress();
                }
                isChange = false;
                mSeekBarThread = new Thread(new SeekBarThread());
                mSeekBarThread.start();
            }
        });

        //
        mPlayerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPlayStatusBinder.isPlaying()) {
                    time = mMediaPlayer.getCurrentPosition();
                    mPlayStatusBinder.pause();
                    flag = true;
                    mPlayerBtn.setSelected(false);
                } else if (flag) {
                    mPlayStatusBinder.resume();
                    flag = false;
                    if (isSeek) {
                        mMediaPlayer.seekTo(time);
                    } else {
                        isSeek = false;
                    }

                    mPlayerBtn.setSelected(true);
                    mSeekBarThread = new Thread(new SeekBarThread());
                    mSeekBarThread.start();
                } else {
                    mMediaPlayer = mPlayStatusBinder.getMediaPlayer();
                    mPlayStatusBinder.play(0);
                    mMediaPlayer.seekTo((int) mSong.getCurrentTime());
                    mPlayerBtn.setSelected(true);
                    mSeekBarThread = new Thread(new SeekBarThread());
                    mSeekBarThread.start();
                }
            }
        });
        mNextIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPlayStatusBinder.next();
                if (mPlayStatusBinder.isPlaying()) {
                    mPlayerBtn.setSelected(true);
                } else {
                    mPlayerBtn.setSelected(false);
                }
            }
        });
    }

    private void addMainFragment() {
        MainFragment mainFragment = new MainFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.add(R.id.fragment_container, mainFragment);
        transaction.commit();
    }

    @Override
    public void onDestroy() {
        unbindService(connection);
        unregisterReceiver(songChangeReceiver);
        Song song = FileHelper.getSong();
        song.setCurrentTime(mPlayStatusBinder.getCurrentTime());
        Log.d(TAG, "onServiceDisconnected: " + song.getCurrentTime());
        FileHelper.saveSong(song);
        super.onDestroy();


    }

    class SeekBarThread implements Runnable {
        @Override
        public void run() {
            while (!isChange && mPlayStatusBinder.isPlaying()) {
                mSeekBar.setProgress((int) mPlayStatusBinder.getCurrentTime());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    class SongChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            mSong = FileHelper.getSong();
            mSongNameTv.setText(mSong.getTitle());
            mSingerTv.setText(mSong.getArtist());
            mSeekBar.setMax((int) mSong.getDuration());
            mSeekBarThread = new Thread(new SeekBarThread());
            mSeekBarThread.start();
        }
    }
}