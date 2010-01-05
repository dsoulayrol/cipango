// ========================================================================
// Copyright 2008-2009 NEXCOM Systems
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.cipango.media;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.cipango.media.rtp.RtpCodec;
import org.cipango.media.rtp.RtpPacket;
import org.mortbay.io.Buffer;
import org.mortbay.io.ByteArrayBuffer;
import org.mortbay.log.Log;

/**
 * Play an audio file in an RTP stream. 
 * 
 * input: mu-law wav file
 * output: rtp stream towards specified destination
 */
public class Player
{
	public static final int DEFAULT_PTIME = 20;
	
	private String _filename;
	private String _host;
	private int _port;
	private int _localPort;
	private int _payloadType;
	
	private SocketAddress _remoteAddress;
    private UdpEndPoint _udpEndPoint;
    
    private AudioInputStream _audioInputStream;
    private int _ptime = DEFAULT_PTIME;
    
    private int _ssrc;
    private int _seqNumber;
    private long _timestamp;
    
    private int _dataLength;
    
    private Buffer _audioBuffer;
    private Buffer _packetBuffer;
    
    public static final int BUFFER_SIZE = 160; // uncompressed bytes
    public static final int PERIOD = 20; // ms
    public static final int DELAY = 10; // ms

    
    private RtpCodec _rtpCodec;

    private Timer _timer;

    private List<PlayerListener> _eventListeners;

    public Player(String filename, String host, int port, int payloadType) 
    {
        super();
       _filename = filename;
       _host = host;
       _port = port;
       _payloadType = payloadType;
       _eventListeners = Collections.synchronizedList(
               new ArrayList<PlayerListener>());
    }
    
    public void init() throws Exception
    {
    	File file = new File(_filename);
        
        _audioInputStream = AudioSystem.getAudioInputStream(file);
        
        Log.info("Playing audio: " + file.getName() + " with format: "
                + _audioInputStream.getFormat());
        
        _remoteAddress = new InetSocketAddress(
                InetAddress.getByName(_host), _port);
        DatagramSocket datagramSocket = new DatagramSocket();
        _udpEndPoint = new UdpEndPoint(datagramSocket);
        _localPort = datagramSocket.getLocalPort();
        
        Random random = new Random();
        _ssrc = random.nextInt();
        
        _dataLength = 8000 * _ptime / 1000;
        
        _audioBuffer = new ByteArrayBuffer(_dataLength);
        _packetBuffer = new ByteArrayBuffer(12 + _dataLength);
        
        _rtpCodec = new RtpCodec();
        _timer = new Timer();
    }

    public void play() 
    {
        _timer.scheduleAtFixedRate(new PlayTimerTask(), DELAY, PERIOD);
    }

    public void stop()
    {
        _timer.cancel();
        try
        {
            _audioInputStream.close();
        }
        catch (IOException e)
        {
            Log.warn("IOException", e);
        }
        try
        {
            _udpEndPoint.close();
        }
        catch (IOException e)
        {
            Log.warn("IOException", e);
        }
    }

    public int getPort()
    {
        return _port;
    }

    public int getLocalPort()
    {
        return _localPort;
    }

    public void addEventListener(PlayerListener playerListener)
    {
        _eventListeners.add(playerListener);
    }

    public void removeEventListener(PlayerListener playerListener)
    {
        _eventListeners.remove(playerListener);
    }

    private void dispatchEvent(PlayerEvent playerEvent)
    {
        for (PlayerListener playerListener: _eventListeners)
            switch (playerEvent)
            {
            case END_OF_FILE:
                playerListener.endOfFile(this);
                break;
            }
    }

    class PlayTimerTask extends TimerTask 
    {
        public void run() 
        {
            int bytesRead;
            try 
            {
                bytesRead = _audioInputStream.read(_audioBuffer.array());
            } 
            catch (IOException e) 
            {
                Log.warn("IOException", e);
                stop();
                return;
            }
            if (bytesRead > 0) 
            {
            	_audioBuffer.setGetIndex(0);
            	_audioBuffer.setPutIndex(bytesRead);
            	
            	RtpPacket packet = new RtpPacket(_ssrc, _seqNumber,
            	        _timestamp, _payloadType);
            	
            	_seqNumber++;
            	_timestamp += bytesRead;
            	
            	packet.setData(_audioBuffer);
            	
            	_packetBuffer.clear();
            	_rtpCodec.encode(_packetBuffer, packet);
            	
                try 
                {
                    _udpEndPoint.send(_packetBuffer, _remoteAddress);
                } 
                catch (IOException e) 
                {
                    Log.warn("IOException", e);
                    stop();
                    return;
                }
                
            }
            else
            {
                stop();
                dispatchEvent(PlayerEvent.END_OF_FILE);
            }
        }
    }

    public static void main(String[] args) throws Exception
    {
    	if (args.length == 0)
    	{
    		System.err.println("Usage: java org.cipango.media.Player " +
    				"audio_file payloadType");
    		System.err.println("audio_file must be a wave file containing " +
    				"pcma or pcmu data");
    		System.err.println("payloadType must be 0 for PCMU 8 for PCMA");
    		System.exit(-1);
    	}
    	
        Player player = new Player(args[0], "127.0.0.1", 6000,
                Integer.parseInt(args[1]));
        player.init();
        player.play();
    }

}