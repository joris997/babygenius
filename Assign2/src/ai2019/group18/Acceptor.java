package ai2019.group18;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

import genius.core.Bid;
import genius.core.boaframework.*;
import genius.core.bidding.BidDetails;
import genius.core.misc.Range;

public class Acceptor extends AcceptanceStrategy{
/**N= Total time of negotiation*/
	    private double N;
		public int a=1;
/**t= Time function during negotiation (0,1)*/
		private double t;
/**value= Threshold utility determinant for acceptance if the opponent is found to be conceding*/
		private double value;
		private SortedOutcomeSpace outcomespace;
		private NegotiationSession negoSes;
		private double upperBound;

		public Acceptor() {

		}
/** Generating an acceptor with inputs from negotiation session and offering strategy*/
		public Acceptor(NegotiationSession negoSession, OfferingStrategy strat) {
			this.negoSes = negoSession;
			this.offeringStrategy = strat;
			
			this.outcomespace = new SortedOutcomeSpace(negoSession.getUtilitySpace());
			negoSession.setOutcomeSpace(outcomespace); 	

		}
/**Setting the outcome space, negotiation session and offering strategy as required*/
		@Override
		public void init(NegotiationSession negoSession, OfferingStrategy strat,
				OpponentModel opponentModel, Map<String, Double> parameters)
				throws Exception {
			outcomespace = new SortedOutcomeSpace(negoSession.getUtilitySpace());
			negoSession.setOutcomeSpace(outcomespace);


			this.negoSes = negoSession;
			this.offeringStrategy = strat;
			
/**Declaring upper and lower bound - for the bids in the initial phase of the offer strategy: This will help us 
//determine the initial acceptance threshold when the opponent is not conceding*/
			this.upperBound = getBound(0.9, 0.95, 0.10);
		}


		@Override
		public Actions determineAcceptability() {
/**Determining the Next bid by the bidding strategy and the opponents current bid*/
			double nextBidUtil = offeringStrategy.getNextBid().getMyUndiscountedUtil();
			double OpponentBidUtil = negoSes.getOpponentBidHistory().getLastBidDetails().
					getMyUndiscountedUtil();
            Boolean conceding = checkOpponentConceding();
			
/**Assigning t and N as defined above   */      
			t=negoSes.getTimeline().getTime();
			N=negoSes.getTimeline().getTotalTime();

/**Declaring the last bid utility for use in the condition of acceptance*/
			double lastBidUtil ;
            Bid lastOwnBid = negoSes.getOwnBidHistory().getLastBid();
            
            if (lastOwnBid == null) {
            	lastBidUtil = 0.001;
			} else {
            	lastBidUtil = negoSes.getUtilitySpace().getUtility(lastOwnBid);
			}

/** If last opponent's bid our own utility is lower than previous utilities then there is no concession */
            if (!conceding)
            {

/**We find out the average of our utility amongst the bids in the outcome space between the upper bound and the max utility*/
            	Range r = new Range(upperBound, 1);
            	List<Double> utils = outcomespace.getBidsinRange(r).stream().map(a->a.getBid()).map(a->negoSes.getUtilitySpace().getUtility(a)).collect(Collectors.toList());
            	double averageUtil = utils.stream().mapToDouble(a->a).average().getAsDouble();

/**Creating a discount factor for our acceptance utility so that if the opponent is not conceding even after 3/4th of the negotiation, 
//we can reduce our acceptance utility till 70% of the initial acceptable utility*/

            	double averageUtilDiscount = averageUtil*(1.832-t);
            	

/**The discount factor comes into play only after 3/4th of the negotiation time is over*/
            	if(t<0.84) {
	                if(OpponentBidUtil>averageUtil) {
	                    return Actions.Accept;
	                }
	                else {
	                    return Actions.Reject;
	                }
            	}
            	else {
            		if(OpponentBidUtil>averageUtilDiscount) {
            			return Actions.Accept;
            		}
            		else {
            			return Actions.Reject;
            		}
            	}
	                
            }
/** If last personal utility is higher than before there is a concession by the opponent*/
            else if(conceding) {

/** We calculate the value function based on the next bid, the previous bid, opponents current bid, and time elapsed,
// such that if value>0 we reject the opponents bids and vice versa*/
                value=Math.pow(nextBidUtil,2)/lastBidUtil-OpponentBidUtil*(N+Math.log(t*Math.sqrt(2)))/N;
                
                if (value>0) {
                    return Actions.Reject;
                }
                else {
                    return Actions.Accept;
                }
            }
            else {
                return Actions.Reject;
            }
		}  

		@Override
		public String getName() {
			return "Acceptor";
		}

/** Determines if the opponent is conceding by checking if the current bid has appeared before within a set horizon of observation. */
	private boolean checkOpponentConceding() {
		int horizon = 5;
/** check if the last received bid has changed w.r.t. previous bids*/
        if (negoSes.getOpponentBidHistory().size() > horizon+1) {
/**Gets the last 5 bids from the opponent's history*/
        	List<Bid> oppBids = negoSes.getOpponentBidHistory().getHistory()
        			.subList(negoSes.getOpponentBidHistory().size()-(horizon+1), negoSes.getOpponentBidHistory().size() - 1)
        			.stream().map(a -> a.getBid()).collect(Collectors.toList());
            
            List<Bid> lastHorizonOffers = oppBids.subList(0, oppBids.size()-2);
            
        	return !lastHorizonOffers.contains(oppBids.get(oppBids.size() - 1));
        }
        return false;
}



 /**
 * Calculates a bound considering two outer values that can not be exceeded and a percentage for which
 * the number of bids should lay in the list from array(reservationvalue) -> array(reservation + percentage)
 */ 
    private double getBound(double lowValueBound, double highValueBound, double percentageBidsCase) {
        double utilityReservationValue = negoSes.getUtilitySpace().getReservationValue();
        Range rangeReservationValue = new Range(utilityReservationValue, 1);

        // obtain a list with biddetails of, for us considered, relevant bids
        List<BidDetails> relevantBids = outcomespace.getBidsinRange(rangeReservationValue);
        // obtain a list with only the utilities of the relevant bids
        List<Double> relevantBidsUtility = relevantBids.stream().map(a->a.getMyUndiscountedUtil()).mapToDouble(a -> a).boxed().collect(Collectors.toList());

        double valueLowUtility = closestValue(lowValueBound, relevantBidsUtility);
        double valueHighUtility = closestValue(highValueBound,relevantBidsUtility);
        int indexLowUtility = relevantBidsUtility.indexOf(valueLowUtility);
        int indexHighUtility = relevantBidsUtility.indexOf(valueHighUtility);

        int indexCasePercent = (int) ((1 - percentageBidsCase) * relevantBids.size());
        if (indexLowUtility < indexCasePercent && indexCasePercent < indexHighUtility) {
            return relevantBids.get(indexCasePercent).getMyUndiscountedUtil();
        } else {
            if (indexLowUtility > indexCasePercent) {
                return lowValueBound;
            }
            if (indexHighUtility < indexCasePercent) {
                return highValueBound;
            } else {
                return (lowValueBound + highValueBound) / 2;
            }
        }
    }
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

}



