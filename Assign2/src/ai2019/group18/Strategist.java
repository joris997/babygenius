package ai2019.group18;

import java.util.List;

import genius.core.bidding.BidDetails;
import genius.core.boaframework.OMStrategy;

public class Strategist extends OMStrategy{
	
	double updateCutoff =  0.5;
	

	@Override
	public BidDetails getBid(List<BidDetails> bidsInRange) {

		BidDetails oppBest = bidsInRange.get(0);
		
		for(BidDetails b:bidsInRange) {
			
			if(model.getBidEvaluation(oppBest.getBid()) < model.getBidEvaluation(b.getBid())) {
				oppBest = b;
			}
		}
		return oppBest; 
	}

	@Override
	public boolean canUpdateOM() { 
			return negotiationSession.getTimeline().getTime() < updateCutoff;
	} 
	
	@Override
	public String getName() {
		return "Strategist";
	}

}
