package com.example.localsocketmediaproxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Environment;
import android.text.TextUtils;

public class MediaProxyServer implements Runnable {
	private static final int TIME_OUT = 0;
	private static final int BUFFER_SIZE = 1024 * 1024;
	private Selector selector;
	private AtomicBoolean isStop;
	private FileChannel fileChannel;
	private long range;
	private AtomicBoolean isNeedReponse;
	
	public MediaProxyServer(int port) {
		try {
			isStop = new AtomicBoolean(false);
			isNeedReponse = new AtomicBoolean(false);
			
			selector = Selector.open();
			ServerSocketChannel serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			serverChannel.socket().bind(new InetSocketAddress(port), 1024);
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		isStop.set(true);
	}
	
	@Override
	public void run() {
		while (!isStop.get()) {
			try {
				selector.select(TIME_OUT);
				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
				while (keys.hasNext()) {
					SelectionKey key = keys.next();
					try {
						handleClientRequest(key);
					} catch (IOException e) {
						e.printStackTrace();
						if (null != key) {
							key.cancel();
							if (null != key.channel()) {
								key.channel().close();
							}
						}
					}
					keys.remove();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (null != selector) {
			try {
				selector.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void handleClientRequest(SelectionKey key) throws IOException {
		if (key.isValid()) {
			// 处理新请求
			if (key.isAcceptable()) {
				// 接受新连接
				ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
				SocketChannel client = ssc.accept();
				client.configureBlocking(false);
				// 将该连接加入selector
				client.register(selector, SelectionKey.OP_READ);
			} // accept end
			
			if (key.isReadable()) {
				String content = readFromClient(key);
				if (isHttpGetRequest(content)) {
					range = parseRange(content);
					isNeedReponse.set(true);
					key.channel().register(selector, SelectionKey.OP_WRITE);
				}
			} // read end
			
			if (key.isWritable()) {
				doWrite(key);
			}
		}
	}
	
	private String readFromClient(SelectionKey key) throws IOException {
		// read data from client
		StringBuffer result = new StringBuffer();
		SocketChannel sc = (SocketChannel) key.channel();
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		while (true) {
			buffer.clear();
			int readBytes = sc.read(buffer);
			if (0 == readBytes) break;
			if (-1 == readBytes) {
				// close connect with client
				key.cancel();
				sc.close();
				break;
			}
			buffer.flip();
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			result.append(new String(bytes));
		}
		return result.toString();
	}
	
	private boolean isHttpGetRequest(String request) {
		return !TextUtils.isEmpty(request) && request.contains("GET");
	}
	
	private long parseRange(String request) {
		if (!TextUtils.isEmpty(request)) {
			String[] heads = request.split("\r\n");
			for (String head : heads) {
				if (head.contains("Range")) {
					final int indexOfEqual = head.lastIndexOf("=");
					final int indexOfMinus = head.lastIndexOf("-");
					return Long.valueOf(head.substring(indexOfEqual + 1, indexOfMinus));
				}
			}
		}
		return 0;
	}
	
	private void doWrite(SelectionKey key) throws IOException {
		openFileChannel();
		writeHttpResponse(key, fileChannel.size(), range);
		writeFileContent(key, fileChannel, range);
	}
	
	private void openFileChannel() throws FileNotFoundException {
		if (null == fileChannel) {
			File test = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ File.separator
					+ "Movies"
					+ File.separator + "test.mp4");
			fileChannel = new RandomAccessFile(test, "r").getChannel();
		}
	}
	
	private void writeHttpResponse(SelectionKey key, long size, long range) throws IOException {
		if (!isNeedReponse.get()) {
			return;
		}
		isNeedReponse.set(false);
		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 206 Partial Content\r\n");
		sb.append("Content-Type: video/mp4\r\n");
		sb.append("Connection: Keep-Alive\r\n");
		sb.append("Accept-Ranges: bytes\r\n");
		sb.append("Content-Length: " + size + "\r\n");
		sb.append("Content-Range: bytes " + range + "-" + (size - 1) + "/" + size + "\r\n");
		sb.append("\r\n");
		
		SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		buffer.clear();
		buffer.put(sb.toString().getBytes());
		buffer.flip();
		while (buffer.hasRemaining()) {
			channel.write(buffer);
		}
		buffer.clear();
	}
	
	private void writeFileContent(SelectionKey key, FileChannel fileChannel, long range) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		buffer.clear();
		
		fileChannel.position(range);
		while (true) {
			if (buffer.hasRemaining()) {
				int bytes = fileChannel.read(buffer);
				if (-1 == bytes) {
					buffer.flip();
					while (buffer.hasRemaining()) {				
						sc.write(buffer);
					}
					buffer.clear();
					
					// close connect with client
					fileChannel.close();
					key.cancel();
					sc.close();
					break;
				}
			} else {
				buffer.flip();
				while (buffer.hasRemaining()) {
					sc.write(buffer);
				}
				buffer.clear();
			}
		}
	}
	
}
