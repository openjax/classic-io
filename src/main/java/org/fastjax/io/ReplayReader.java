/* Copyright (c) 2016 FastJAX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.fastjax.io;

import java.io.CharArrayWriter;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * This class implements a FilterReader that allows its content to be re-read.
 * With each call to its read methods, content is written to an underlying
 * buffer that automatically grows. This implementation supports a maximum
 * re-readable buffer length of {@code Integer.MAX_VALUE}.
 */
public class ReplayReader extends FilterReader {
  /**
   * A character writer that exposes readback APIs.
   */
  protected class ReadbackCharArrayWriter extends CharArrayWriter {
    private int total;
    private int mark;

    /**
     * Creates a new ReadbackCharArrayWriter with the specified initial size.
     *
     * @param initialSize An int specifying the initial buffer size.
     * @throws IllegalArgumentException If initialSize is negative.
     */
    public ReadbackCharArrayWriter(final int initialSize) {
      super(initialSize);
    }

    /**
     * Creates a new ReadbackCharArrayWriter.
     */
    public ReadbackCharArrayWriter() {
      super();
    }

    /**
     * Returns the buffer where data is stored.
     *
     * @return The buffer where data is stored.
     */
    public char[] buf() {
      return buf;
    }

    @Override
    public CharArrayWriter append(final char c) {
      final CharArrayWriter writer = super.append(c);
      ++total;
      return writer;
    }

    @Override
    public CharArrayWriter append(final CharSequence csq) {
      final CharArrayWriter writer = super.append(csq);
      total += csq.length();
      return writer;
    }

    @Override
    public CharArrayWriter append(final CharSequence csq, final int start, final int end) {
      final CharArrayWriter writer = super.append(csq, start, end);
      total += end - start;
      return writer;
    }

    @Override
    public void write(final int c) {
      super.write(c);
      ++total;
    }

    @Override
    public void write(final char[] c, final int off, final int len) {
      super.write(c, off, len);
      total += len;
    }

    @Override
    public void write(final String str, final int off, final int len) {
      super.write(str, off, len);
      total += len;
    }

    @Override
    public void write(final char[] cbuf) throws IOException {
      super.write(cbuf);
      total += cbuf.length;
    }

    @Override
    public void write(final String str) throws IOException {
      super.write(str);
      total += str.length();
    }

    /**
     * Reads a single character from the buffer.
     *
     * @return The character read, as an integer in the range 0 to 65535
     *         ({@code 0x00-0xffff}), or -1 if the end of the buffer has been
     *         reached.
     */
    public int read() {
      return count >= total ? -1 : buf[count++];
    }

    /**
     * Reads characters into an array.
     *
     * @param cbuf Destination buffer.
     * @return The number of characters read, or -1 if the end of the stream has
     *         been reached.
     */
    public int read(final char[] cbuf) {
      return read(cbuf, 0, cbuf.length);
    }

    /**
     * Reads characters into a portion of an array.
     *
     * @param cbuf Destination buffer.
     * @param off Offset at which to start storing characters.
     * @param len Maximum number of characters to read.
     * @return The number of characters read, or -1 if the end of the stream has
     *         been reached.
     * @throws IndexOutOfBoundsException If {@code off} is negative, or
     *           {@code len} is negative, or {@code len} is greater than
     *           {@code cbuf.length - off}.
     */
    public int read(final char[] cbuf, final int off, final int len) {
      if (count >= total)
        return -1;

      final int delta = len - available() - 1;
      final int length = 0 < delta ? len - delta : len;
      System.arraycopy(buf, count, cbuf, off, length);
      count += length;
      return length;
    }

    /**
     * Skips characters.
     *
     * @param n The number of characters to skip
     * @return The number of characters actually skipped.
     * @throws IllegalArgumentException If {@code n} is negative.
     */
    public long skip(final long n) {
      if (n < 0)
        throw new IllegalArgumentException("Skip value is negative: " + n);

      return skip0(n);
    }

    /**
     * Skips characters.
     *
     * @param n The number of characters to skip
     * @return The number of characters actually skipped.
     */
    private long skip0(final long n) {
      if (count >= total)
        return 0;

      if (n <= 0)
        return 0;

      final long check = total - count - n;
      final long length = check < 0 ? n + check : n;
      count += length;
      return length;
    }

    /**
     * Returns the number of characters available to read from the buffer.
     *
     * @return The number of characters available to read from the buffer.
     */
    public int available() {
      return total - count;
    }

    /**
     * Marks the present position in the reader. Subsequent calls to
     * {@link #reset()} will attempt to reposition the stream to this point.
     */
    public void mark() {
      mark = count;
    }

    /**
     * Resets the buffer to the position value of the argument.
     *
     * @param p The position to reset to.
     */
    private void reset0(final int p) {
      if (p > count)
        skip0(p - count);

      count = p;
    }

    /**
     * Resets the buffer to the position value of the argument.
     *
     * @param p The position to reset to.
     * @throws IllegalArgumentException If {@code p} is negative, or if
     *           {@code p} exceeds the buffer length.
     */
    public void reset(final int p) {
      if (p < 0)
        throw new IllegalArgumentException("Position (" + p + ") must be non-negative");

      if (total < p)
        throw new IllegalArgumentException("Position (" + p + ") must not exceed buffer length (" + total + ")");

      reset0(p);
    }

    /**
     * Resets the buffer to the position previously marked by {@link #mark()}.
     */
    @Override
    public void reset() {
      reset0(mark);
    }

    /**
     * Resets the position of the writer to 0. This method does not release the
     * buffer, such that it can be re-read.
     */
    @Override
    public void close() {
      count = 0;
    }
  }

  protected final ReadbackCharArrayWriter buffer;
  private boolean closed;

  /**
   * Creates a new ReplayReader.
   *
   * @param in A Reader object providing the underlying stream.
   * @param initialSize an int specifying the initial buffer size of the
   *          re-readable buffer.
   * @throws NullPointerException If {@code in} is null.
   */
  public ReplayReader(final Reader in, final int initialSize) {
    super(in);
    this.buffer = new ReadbackCharArrayWriter(initialSize);
  }

  /**
   * Creates a new ReplayReader.
   *
   * @param in A Reader object providing the underlying stream.
   * @throws NullPointerException If {@code in} is null.
   */
  public ReplayReader(final Reader in) {
    super(in);
    this.buffer = new ReadbackCharArrayWriter();
  }

  /**
   * Tells whether this stream is ready to be read. If the reader's position was
   * previously reset such that the buffer has a character available to be
   * re-read, this method returns {@code true}. Otherwise, this method is
   * delegated to the underlying reader.
   *
   * @return {@code true} if the reader's position was previously reset such
   *         that the buffer has a character available to be re-read. Otherwise,
   *         this method is delegated to the underlying reader.
   * @throws IOException If an I/O error has occurred.
   */
  @Override
  public boolean ready() throws IOException {
    return buffer.available() > 0 || in.ready();
  }

  /**
   * Reads a single character. If the reader's position was previously reset
   * such that the buffer has a character available to be re-read, the character
   * will be re-read from the underlying buffer. Otherwise, a character will be
   * read from the underlying stream, in which case this method will block until
   * a character is available, an I/O error occurs, or the end of the stream is
   * reached.
   *
   * @throws IOException If an I/O error has occurred.
   * @return The character read, as an integer in the range 0 to 65535
   *         ({@code 0x00-0xffff}), or -1 if the end of the stream has been
   *         reached.
   */
  @Override
  public int read() throws IOException {
    if (buffer.available() > 0)
      return buffer.read();

    if (closed)
      return -1;

    final int ch = in.read();
    if (ch != -1)
      buffer.write(ch);

    return ch;
  }

  /**
   * Reads characters into an array. If the reader's position was previously
   * reset such that the buffer has characters available to be re-read, the
   * available characters will be re-read from the underlying buffer. The
   * remaining characters will be read from the underlying stream, in which case
   * this method will block characters are available, an I/O error occurs, or
   * the end of the stream is reached.
   *
   * @param cbuf Destination buffer.
   * @return The number of characters read, or -1 if the end of the stream has
   *         been reached.
   * @throws IOException If an I/O error has occurred.
   */
  @Override
  public int read(final char[] cbuf) throws IOException {
    return read(cbuf, 0, cbuf.length);
  }

  /**
   * Reads characters into a portion of an array. If the reader's position was
   * previously reset such that the buffer has characters available to be
   * re-read, the available characters will be re-read from the underlying
   * buffer. The remaining characters will be read from the underlying stream,
   * in which case this method will block characters are available, an I/O error
   * occurs, or the end of the stream is reached.
   *
   * @param cbuf Destination buffer.
   * @param off Offset at which to start storing characters.
   * @param len Maximum number of characters to read.
   * @return The number of characters read, or -1 if the end of the stream has
   *         been reached.
   * @throws IOException If an I/O error has occurred.
   * @throws IndexOutOfBoundsException If {@code off} is negative, or
   *           {@code len} is negative, or {@code len} is greater than
   *           {@code cbuf.length - off}.
   */
  @Override
  public int read(final char[] cbuf, final int off, final int len) throws IOException {
    int avail = buffer.available();
    if (avail >= len)
      return buffer.read(cbuf, off, len);

    if (avail > 0) {
      buffer.read(cbuf, off, avail);
      for (int ch; avail < cbuf.length - off && (ch = read()) != -1; cbuf[avail++] = (char)ch);
      return avail;
    }

    final int ch = in.read(cbuf, off, len);
    if (ch > 0)
      buffer.write(cbuf, off, ch);

    return ch;
  }

  /**
   * Skips characters. If the reader's position was previously reset such that
   * the buffer has characters available to be re-read, the available characters
   * will first be skipped in the underlying buffer. The remaining characters
   * will be read from the underlying stream, written to the buffer, and
   * skipped, in which case this method will block characters are available, an
   * I/O error occurs, or the end of the stream is reached.
   *
   * @param n The number of characters to skip
   * @return The number of characters actually skipped.
   * @throws IllegalArgumentException If {@code n} is negative.
   * @throws IOException If an I/O error has occurred.
   */
  @Override
  public long skip(final long n) throws IOException {
    if (n < 0)
      throw new IllegalArgumentException("Skip value is negative: " + n);

    int avail = buffer.available();
    if (avail >= n)
      return buffer.skip(n);

    if (avail > 0) {
      buffer.skip(avail);
      while (avail++ < n && read() != -1);
      return avail;
    }

    while (read() != -1 && ++avail < n);
    return avail;
  }

  /**
   * Marks the present position in the stream. Subsequent calls to
   * {@link #reset()} will attempt to reposition the stream to this point.
   *
   * @param readlimit This argument is ignored.
   */
  @Override
  public void mark(final int readlimit) {
    buffer.mark();
  }

  /**
   * Tells whether this stream supports the {@link #mark(int)} operation, which
   * is always {@code true}.
   *
   * @return {@code true}.
   */
  @Override
  public boolean markSupported() {
    return true;
  }

  /**
   * Resets the stream to a location previously marked with the
   * {@link #mark(int)} method.
   */
  @Override
  public void reset() {
    buffer.reset();
  }

  /**
   * Closes the underlying underlying reader resource, and resets the underlying
   * buffer position to 0. Subsequent calls to {@code read()}, {@code mark()}
   * and {@code reset()} continue to function as before the underlying stream
   * was closed. The purpose of this method is solely to release the underlying
   * stream once its content has been satisfactorily read.
   *
   * @throws IOException If an I/O error has occurred.
   */
  @Override
  public void close() throws IOException {
    buffer.close();
    in.close();
    closed = true;
  }
}