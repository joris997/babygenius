package ai2019.group18;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import agents.uk.ac.soton.ecs.gp4j.util.ArrayUtils;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.*;
import genius.core.misc.Range;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.utility.AbstractUtilitySpace;


public class Bidder extends OfferingStrategy{
	// List containing all offers made by the opponent
	private BidDetails lastReceivedOffer;
	public int a=1;
	private NegotiationSession negoSes;
	private SortedOutcomeSpace outcomespace;
	private Random r = new Random();
	// Lower utility bound for bids made in the learning phase
	private double lowerBound;
	// Upper utility bound for bids made in the learning phase
	private double upperBound;
	private double prevBidUtil;
	private boolean startConc = false;
	private double certaintyConc = 0.5;
	private OpponentModel oppModel;
	private OMStrategy strat;
	
	@Override
	public void init(NegotiationSession negoSession, OpponentModel model, OMStrategy oms,
					 Map<String, Double> parameters) throws Exception {

		super.init(negoSession, parameters);

		this.outcomespace = new SortedOutcomeSpace(negoSession.getUtilitySpace());
		negoSession.setOutcomeSpace(outcomespace);

		this.negoSes = negoSession;
		this.oppModel = model;

		// Info on lower and upper value that the upper or lowerbound can take on, plus the percentage of bids from top
		// down that should lie in this bound
		this.upperBound = getBound(0.9, 0.95, 0.15);
		this.lowerBound = getBound(0.82, 0.85, 0.30);
		
		this.strat = oms;
			
//		for(int i=0; i<10; i++) {
//			//System.out.println("ITERATION: " + i);
//			Bid randomBid = negoSes.getUtilitySpace().getDomain().getRandomBid(new Random());
////			log("The estimate of the utility of a random bid (" + randomBid	+ ") is: " + negoSes.getUtilitySpace().getUtility(randomBid));
//	
//			if (negoSes.getUserModel() instanceof ExperimentalUserModel) 
//			{
////				log("You have given the agent access to the real utility space for debugging purposes.");
////				ExperimentalUserModel e = (ExperimentalUserModel) negoSes.getUserModel();
////				AbstractUtilitySpace realUSpace = e.getRealUtilitySpace();
////	
////				log("The real utility space is: " + realUSpace);
////				log("The real utility of the random bid is: "
////						+ realUSpace.getUtility(randomBid));
//			}
//		}
	}

	@Override
	/**
	 * Determines the opening bid. In this case it's a bid between two thresholds for the learning phase.
	 */
	public BidDetails determineOpeningBid() {
		return generateBidLearning();
	}

	@Override
	/**
	 * Depending on the time and opponent behaviour, the next bid is determined.
	 */
	public BidDetails determineNextBid() {
		lastReceivedOffer = negoSes.getOpponentBidHistory().getLastBidDetails();

		//If we are not making the first bid
		if (lastReceivedOffer != null) {
			// Check whether the opponent is conceding (true) or not (false)
			Boolean conceding = checkOpponentConceding();

			// Using the previously obtained checkOpponentConceding() returns, depending on the frequency, update the
			// certainty that we have in that the opponent is actually conceding or it is just a random occurrence
			updateCertainty(conceding);

			// Once the opponent starts conceding, or 85% the negotiation time has passed we start increasing utility
			if(((startConc | (negoSes.getTimeline().getTime() >= 0.85)) && negoSes.getTimeline().getTime() < 0.96)) {
				return generateBidLying();
			}

			// If we are over 96% of the allotted time, we start slowly conceding
			else if ((negoSes.getTimeline().getTime() >= 0.96)& negoSes.getOpponentBidHistory().getHistory().size()>=5){
				return generateBidConceding();
			}
			
			else if((negoSes.getTimeline().getTime() >= 0.96)& negoSes.getOpponentBidHistory().getHistory().size()<5) {
				return negoSes.getOutcomeSpace().getBidNearUtility(1.0);
			}
			//Otherwise, for the first 85% of the time or until the opponent starts conceding, generate random bids between two bounds
			else {
				return generateBidLearning();
			}
		}
		else {
			// If none of the above are true, generate random bids between two thresholds to prevent the opponent
			// accurately estimating our utility space.
			return generateBidLearning();
		}
	}

	@Override
	/**
	 * Returns component name
	 */
	public String getName() {
		return "Bidder";
	}


	/**
	 * Determines if the opponent is conceding by checking if the current bid has appeared before within a set horizon of observation.
	 * @return
	 */
	private boolean checkOpponentConceding() {
		// For checking if the opponent is conceding, compare current bid to last 5 bids
		int horizon = 5;
		// Check if the last received bid has changed w.r.t. previous bids
		if (negoSes.getOpponentBidHistory().size() > horizon+1) {
			// Gets the last 5 bids from the opponent's history
			List<Bid> oppBids = negoSes.getOpponentBidHistory().getHistory()
					.subList(negoSes.getOpponentBidHistory().size()-(horizon+1), negoSes.getOpponentBidHistory().size() - 1)
					.stream().map(a -> a.getBid()).collect(Collectors.toList());

			List<Bid> lastHorizonOffers = oppBids.subList(0, oppBids.size()-2);

			// If the current bid by the opponent does not occur in its previous 5 bids (or depending on the size of the
			// horizon) return 'true' which states that we think the opponent is conceding with his offer
			return !lastHorizonOffers.contains(oppBids.get(oppBids.size() - 1));
		}
		return false;
	}

	/**
	 * Updates the certainty that the opponent is conceding
	 * @param conceding - boolean estimate whether or not an opponent is conceding
	 */
	private void updateCertainty(boolean conceding) {
		// If for the current bid the opponent is found to be conceding
		if (conceding) {
			// Set a start variable to true
			startConc = true;
			// And add 0.1 to the certainty of the opponent actually conceding
			certaintyConc = certaintyConc + 0.1;

			// Limit certainty to a value of 1
			if(certaintyConc > 1){
				certaintyConc = 1;
			}
		}
		// If for the current bid the opponent is found not to be conceding
		else{
			// If the opponent has started conceding in the past
			if(startConc){
				// Subtract 0.1 from the certainty of the opponent actually conceding
				certaintyConc = certaintyConc - 0.1;
			}

			// Limit certainty to a value of 0
			if(certaintyConc < 0){
				certaintyConc = 0;
			}
		}
	}

	/**
	 * Generates bids with step-by-step higher utility than the previous bids.
	 * @return biddetails of a 'lying' bid
	 */
	private BidDetails generateBidLying() {
		// Indicate to the user that the following bid is a lying bid
		
		BidDetails newBidDetails;

		// Obtain the previously made bid
		prevBidUtil = negoSes.getOwnBidHistory().getLastBidDetails().getMyUndiscountedUtil();
		// List of bids with utilities ranging from our previous bid to 1
		List<BidDetails> bidList = negoSes.getOutcomeSpace().getBidsinRange(new Range(prevBidUtil, 1.0));
		// Average time required for making a bid
		double timePerBid = negoSes.getTimeline().getTime()/(negoSes.getOpponentBidHistory().size() + negoSes.getOwnBidHistory().size());

		// Number of bids available in the observed range
		double bidsInRange = (double) bidList.size();
		
		// Bids our agent is physically capable of making in the allotted time
		double bidsAvailable = Math.floor((0.96 - negoSes.getTime())/timePerBid);

		double speedFactor = 1.0;
		BidHistory oppHist = negoSes.getOpponentBidHistory();
		Bid oppPrePrevBid = oppHist.getHistory().get(oppHist.getHistory().size()-2).getBid();
		Bid oppPrevBid = oppHist.getLastBid();

		/*
		 * Calculates the speed factor which alters the speed at which we are lying in accordance to the speed that the opponent is conceding
		 * Helps in getting the most out of the lying phase based off of opponent behaviour
		 */
		if(negoSes.getOpponentBidHistory().size() > 2) {
			if(oppModel.getBidEvaluation(oppPrePrevBid) != oppModel.getBidEvaluation(oppPrevBid)) {
				speedFactor = ((oppModel.getBidEvaluation(oppPrePrevBid) + oppModel.getBidEvaluation(oppPrevBid)))
						/Math.abs(Math.log(Math.abs(oppModel.getBidEvaluation(oppPrePrevBid) - oppModel.getBidEvaluation(oppPrevBid))));
			}
			else {
				speedFactor = 1;
			}
		}


		double bidStep = (bidsInRange/bidsAvailable)*speedFactor+prevBidUtil;//*speedFactor;//*certaintyConc*speedFactor;
		
		//Find the bid closest to the next wanted utility
		newBidDetails = negoSes.getOutcomeSpace().getBidNearUtility(bidStep);


		if (newBidDetails != null) {
			return newBidDetails;
		}
		//In case the new bidDetails are null, generate random bid to avoid runtime errors
		else {
			Bid newBid = negoSes.getDomain().getRandomBid(r);
			return new BidDetails(newBid, negoSes.getUtilitySpace().getUtility(newBid));
		}
	}

	/**
	 * Generates a bid that actually concedes w.r.t. our utility
	 * @return biddetails of a 'conceding' bid
	 */
	private BidDetails generateBidConceding() {
		Bid pastBids;
		BidDetails newBid = null;
		//double delta = 0.001;

		//Extract our previous bid utility
		double prevBidUtil = negoSes.getOwnBidHistory().getLastBidDetails().getMyUndiscountedUtil();
		double[] U = new double[5];

		//Extract the opponent's last five utilities
		int horizon = 5;
		List<BidDetails> oppBidDetails = negoSes.getOpponentBidHistory().getHistory().subList(negoSes.getOpponentBidHistory().size() - 6, negoSes.getOpponentBidHistory().size() - 1);

		for(int i=0; i<horizon; i++) {
			pastBids = oppBidDetails.get(i).getBid();
			U[i]=negoSes.getUtilitySpace().getUtility(pastBids);
		}
		Double [] obj =  ArrayUtils.toObject(U);
		
		//Calculate opponent's average utility in the past five steps
		double U_av = Arrays.asList(obj).stream().mapToDouble(a -> a).average().getAsDouble();

		double[] utility = new double[3];
		
		//Calculate utility of conceding bid
		if(negoSes.getTimeline().getTime() <= 0.99) {
			utility[0] = prevBidUtil - Math.abs(U_av-U[4])/(Math.exp(Math.abs(100-100*negoSes.getTimeline().getTime())*Math.abs(U_av-U[4])/prevBidUtil)+1);
		}
		else {
			utility[0] = prevBidUtil - Math.abs(U_av-U[4])/(Math.exp(Math.abs(100-100*0.99)*Math.abs(U_av-U[4])/prevBidUtil)+1);
		}
		
		
		utility[1] = (prevBidUtil - utility[0])/3 + utility[0];
		utility[2] = 2*(prevBidUtil - utility[0])/3 + utility[0];
		
		//Check that the opponent's best utility out of the bids in the range of from the agent's previous to current bid
		//is lower than the new utility we plan to offer
		for(int i=0; i<3; i++) {
			Range utilRange = new Range(utility[i], prevBidUtil);
			List<BidDetails> bidsInRange = outcomespace.getBidsinRange(utilRange);
			BidDetails oppBest = strat.getBid(bidsInRange);
			
			newBid = oppBest;
			if(oppModel.getBidEvaluation(oppBest.getBid())<utility[i]) {
				break;
			}
		}
		


		if (newBid != null) {
			return newBid;
		}
		//In case the new bidDetails are null, generate random bid to avoid runtime errors
		else {
			Bid newBidBackup = negoSes.getDomain().getRandomBid(r);
			return new BidDetails(newBidBackup, negoSes.getUtilitySpace().getUtility(newBidBackup));
		}
	}

	/**
	 * Generates random bids between two thresholds
	 * @return biddetails of a random bid during the phase in which we will learn the opponents profile
	 */
	private BidDetails generateBidLearning() {

		int i = 0;
		// Compute random bid within our threshold bounds
		Random rUtil = new Random();
		// Generate a random utility between the two utility bounds
		double randomUtil = lowerBound + (upperBound - lowerBound) * rUtil.nextDouble();
		BidDetails newBidDetails = negoSes.getOutcomeSpace().getBidNearUtility(randomUtil);

		// If the bid has a utility greater than 0.7 for the opponent, generate a new offer, until this is no longer the case,
		// or until 20 attempts have been made
		while(oppModel.getBidEvaluation(newBidDetails.getBid())>0.7 && i<20) {
			randomUtil = lowerBound + (upperBound - lowerBound) * rUtil.nextDouble();
			newBidDetails = negoSes.getOutcomeSpace().getBidNearUtility(randomUtil);
			i++;
		}


		if (newBidDetails != null) {
			return newBidDetails;
		}
		//In case the new bidDetails are null, generate random bid to avoid runtime errors
		else {
			Bid newBid = negoSes.getDomain().getRandomBid(r);
			return new BidDetails(newBid, negoSes.getUtilitySpace().getUtility(newBid));
		}
	}

	/**
	 * Calculates a bound considering two outer values that can not be exceeded and a percentage for which
	 * the number of bids should lay in the list from array(reservationvalue) -> array(reservation + percentage)
	 * @param lowValueBound determines the absolute lowest value that that specific bound can take on
	 * @param highValueBound determines the absolute highest value that that specific bound can take on
	 * @param percentageBidsCase determines the number of bids that (from top down) should be inside the bound to be
	 *                           found and the best bid (1 utility)
	 * @return bound utility value
	 */
	private double getBound(double lowValueBound, double highValueBound, double percentageBidsCase) {
		// Obtain the utility of the reservation value
		double utilityReservationValue = negoSes.getUtilitySpace().getReservationValue();
		// Create a range from reservation value utility to 1 utility
		Range rangeReservationValue = new Range(utilityReservationValue, 1);
		// Obtain a list with biddetails of, for us considered, relevant bids
		List<BidDetails> relevantBids = outcomespace.getBidsinRange(rangeReservationValue);
		// Obtain a list with only the utilities of the relevant bids
		List<Double> relevantBidsUtility = relevantBids.stream().map(a->a.getMyUndiscountedUtil()).mapToDouble(a -> a).boxed().collect(Collectors.toList());

		// Check which utility lays closest to lowerbound value or upperbound value
		double valueLowUtility = closestValue(lowValueBound, relevantBidsUtility);
		double valueHighUtility = closestValue(highValueBound,relevantBidsUtility);
		// Obtain its respective index
		int indexLowUtility = relevantBidsUtility.indexOf(valueLowUtility);
		int indexHighUtility = relevantBidsUtility.indexOf(valueHighUtility);

		// Obtain the index where the sufficient amount of bids (percentage wise) is present, either for lying (15 %) or
		// for learning (35 %)
		int indexCasePercent = (int) ((1 - percentageBidsCase) * relevantBids.size());

		// Check whether the index of the desired percentage of bids is in between the bounds
		if (indexLowUtility < indexCasePercent && indexCasePercent < indexHighUtility) {
			return relevantBids.get(indexCasePercent).getMyUndiscountedUtil();
		} else {
			// Otherwise, check if it is lower than the lower bound
			if (indexLowUtility > indexCasePercent) {
				// And then return the lower bound (since we should not go any lower as to maintain an upper position in
				// the bids that we make
				return lowValueBound;
			}
			// Or, check if it is higher than the upper bound
			if (indexHighUtility < indexCasePercent) {
				// And return the equivalent
				return highValueBound;
			} else {
				// If somehow all of this fails, return the value between the upper and lower bound
				return (lowValueBound + highValueBound) / 2;
			}
		}
	}

	/**
	 * Returns the value from a list that is closest to the input value.
	 * @param of the value of which the closest in a list has to be found
	 * @param in the respective list
	 * @return the value that lays closest to 'of' in the list 'in'
	 */
	private double closestValue(double of, List<Double> in){
		double min = Double.MAX_VALUE;
		double closest = of;
		for (double v : in){
			final double diff = Math.abs(v - of);
			if (diff < min){
				min = diff;
				closest = v;
			}
		}
		return closest;
	}
	
	
	
	/**
	 * Prints out the inputed string. Used for logging in the case of preference uncertainty.
	 * @param s
	 */
	private static void log(String s) 
	{
		System.out.println(s);
	}
	
}
