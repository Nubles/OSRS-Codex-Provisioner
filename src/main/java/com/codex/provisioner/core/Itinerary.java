package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * A sitting as a trip: each piece of work at the spot it is done at, picked as the one quickest to
 * reach from the last (with the teleports the account owns, from Codex Wayfinder's atlas), and the
 * travel between them counted inside the sitting's time. The sitting is planned again with the
 * travel taken out of its length, so what it promises fits once the walking is done.
 */
public final class Itinerary
{
	/** One stop: the travel to it, then the work there. */
	public static final class Stop
	{
		public final Sitting.Piece piece;
		/** Where it is done, or null for anywhere. */
		public final Spots.Spot spot;
		/** The way there from the last stop, or null when it needs no travel or none is known. */
		public final Travel.Leg leg;

		Stop(Sitting.Piece piece, Spots.Spot spot, Travel.Leg leg)
		{
			this.piece = piece;
			this.spot = spot;
			this.leg = leg;
		}

		public long travelMs()
		{
			return leg == null ? 0 : leg.seconds * 1000L;
		}
	}

	private final Sitting sitting;
	private final List<Stop> stops;
	private final long travelMs;

	private Itinerary(Sitting sitting, List<Stop> stops, long travelMs)
	{
		this.sitting = sitting;
		this.stops = stops;
		this.travelMs = travelMs;
	}

	public Sitting getSitting()
	{
		return sitting;
	}

	public List<Stop> getStops()
	{
		return stops;
	}

	public long getTravelMs()
	{
		return travelMs;
	}

	/** Work and travel together. */
	public long getTotalMs()
	{
		return sitting.getUsed() + travelMs;
	}

	/** Teleports in the bank are taken to cost a minute's bank stop: a planning assumption. */
	static final int BANK_STOP_SECONDS = 60;
	private static final int PASSES = 3;

	/**
	 * @param travel Codex Wayfinder's atlas, or null to plan the sitting without travel
	 * @param here where the player stands, or null
	 */
	public static Itinerary plan(Chain chain, Map<Integer, Long> estimates, Set<Integer> grown, long budget,
		Map<Skill, Integer> levels, Travel travel, int[] here)
	{
		Sitting s = Sitting.plan(chain, estimates, grown, budget);
		Itinerary it = route(s, levels, travel, here);
		for (int pass = 0; pass < PASSES && travel != null && it.travelMs > 0 && it.getTotalMs() > budget; pass++)
		{
			s = Sitting.plan(chain, estimates, grown, Math.max(0, budget - it.travelMs));
			it = route(s, levels, travel, here);
		}
		return it;
	}

	static Itinerary route(Sitting s, Map<Skill, Integer> levels, Travel travel, int[] here)
	{
		List<Stop> stops = new ArrayList<>();
		int[] at = here;
		long total = 0;
		for (Sitting.Piece p : s.getPieces())
		{
			Spots.Spot best = null;
			Travel.Leg bestLeg = null;
			for (Spots.Spot spot : Spots.of(p.getStep().getItem()))
			{
				if (!spot.open(levels))
				{
					continue;
				}
				Travel.Leg leg = travel == null ? null : travel.to(at, spot.x, spot.y, spot.plane, BANK_STOP_SECONDS);
				if (best == null || leg != null && (bestLeg == null || leg.seconds < bestLeg.seconds))
				{
					best = spot;
					bestLeg = leg;
				}
			}
			// Already there: no trip.
			if (best != null && bestLeg != null && bestLeg.via == null && bestLeg.walkTiles <= 15)
			{
				bestLeg = null;
			}
			Stop stop = new Stop(p, best, bestLeg);
			total += stop.travelMs();
			if (best != null)
			{
				at = new int[]{best.x, best.y, best.plane};
			}
			stops.add(stop);
		}
		return new Itinerary(s, Collections.unmodifiableList(stops), total);
	}
}
