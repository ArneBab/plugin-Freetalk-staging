/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.Board;
import plugins.Freetalk.Message;
import plugins.Freetalk.exceptions.NoSuchMessageException;

/**
 * Object representing a newsgroup, as seen from the NNTP client's
 * point of view.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPGroup {
	private final Board board;

	public FreetalkNNTPGroup(Board board) {
		this.board = board;
	}

	/**
	 * Get the FTBoard object associated with this group.
	 */
	public Board getBoard() {
		return board;
	}

	/**
	 * Estimate number of messages that have been posted.
	 */
	public long messageCount() {
		return board.getAllMessages().size();
	}

	/**
	 * Get the first valid message number.
	 */
	public int firstMessage() {
		return 1;				// FIXME
	}

	/**
	 * Get the last valid message number.
	 */
	public int lastMessage() {
		return 0;				// FIXME
	}

	/**
	 * Get the article with the given index
	 */
	public FreetalkNNTPArticle getMessage(int messageNum) throws NoSuchMessageException {
		Message msg = board.getMessageByIndex(messageNum);
		return new FreetalkNNTPArticle(msg, messageNum);
	}

	/**
	 * Get the board posting status.  This is normally either "y"
	 * (posting is allowed), "n" (posting is not allowed), or "m"
	 * (group is moderated.)  It is a hint to the reader and doesn't
	 * necessarily indicate whether the client will be allowed to
	 * post, or whether any given message will be accepted.
	 */
	public String postingStatus() {
		return "y";
	}
}
