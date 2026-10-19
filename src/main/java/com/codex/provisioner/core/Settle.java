package com.codex.provisioner.core;

/**
 * Counts the game ticks after a login or a world hop until the account can be read. The game
 * says the player is logged in before it has sent their levels and varbits, which arrive over the
 * first ticks; read too soon, every skill is level 1 and no account is an ironman. RuneLite's own
 * experience tracker waits two ticks the same way.
 */
public final class Settle
{
	/** Ticks after logging in before levels and varbits are trusted. */
	static final int TICKS = 2;

	private int wait = TICKS;

	/** Logging in or hopping: the account is not to be read until it has settled. */
	public void restart()
	{
		wait = TICKS;
	}

	/**
	 * A game tick passed while logged in.
	 *
	 * @return true on the one tick the account settles, when it should be read afresh
	 */
	public boolean tick()
	{
		if (wait <= 0)
		{
			return false;
		}
		wait--;
		return wait == 0;
	}

	/** Whether levels and varbits read now are the account's own. */
	public boolean isSettled()
	{
		return wait <= 0;
	}
}
