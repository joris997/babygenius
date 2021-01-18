package ai2019.group18;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.DoubleStream;

import genius.core.Bid;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.BoaParty;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AbstractUtilitySpace;

/**
 * This example shows how BOA components can be made into an independent
 * negotiation party and which can handle preference uncertainty.
 * 
 * Note that this is equivalent to adding a BOA party via the GUI by selecting
 * the components and parameters. However, this method gives more control over
 * the implementation, as the agent designer can choose to override behavior
 * (such as handling preference uncertainty).
 * <p>
 * For more information, see: Baarslag T., Hindriks K.V., Hendrikx M.,
 * Dirkzwager A., Jonker C.M. Decoupling Negotiating Agents to Explore the Space
 * of Negotiation Strategies. Proceedings of The Fifth International Workshop on
 * Agent-based Complex Automated Negotiations (ACAN 2012), 2012.
 * https://homepages.cwi.nl/~baarslag/pub/Decoupling_Negotiating_Agents_to_Explore_the_Space_of_Negotiation_Strategies_ACAN_2012.pdf
 * 
 * @author Tim Baarslag
 */
@SuppressWarnings("serial")
public class UncertaintyBOA extends BoaParty 
{ 
	//public NegotiationInfo info;
	@Override
	public void init(NegotiationInfo info) 
	{
		// The choice for each component is made here
		AcceptanceStrategy 	ac  = new Acceptor();
		OfferingStrategy 	os  = new Bidder();
		OpponentModel 		om  = new OM();
		OMStrategy			oms = new Strategist();
		
		Map<String, Double> noparams = Collections.emptyMap();


		
		// Initialize all the components of this party to the choices defined above
		configure(ac, noparams, 
				os,	noparams, 
				om, noparams,
				oms, noparams);
		super.init(info);
	}

	/**
	 * Specific functionality, such as the estimate of the utility space in the
	 * face of preference uncertainty, can be specified by overriding the
	 * default behavior.
	 */
	@Override
	public  AbstractUtilitySpace estimateUtilitySpace() 
	{
		AdditiveUtilitySpaceFactory additiveUtilitySpaceFactory = new AdditiveUtilitySpaceFactory(getDomain());
		List<IssueDiscrete> issues = additiveUtilitySpaceFactory.getIssues();
		
		for (IssueDiscrete i : issues)
		{
			
			additiveUtilitySpaceFactory.setWeight(i, Attributeweightcalc().get(i));
			
			for (ValueDiscrete v : i.getValues()) {
				
				for(int l=0;l<issueVals().values().toArray().length;l++){
					if (issueVals().keySet().toArray()[l].toString().equals(v.toString())){
						additiveUtilitySpaceFactory.setUtility(i, v, (double) issueVals().values().toArray()[l]);
					}
				}	
			}
		}
		// Normalize the weights, since we picked them randomly in [0, 1]
		//additiveUtilitySpaceFactory.normalizeWeights();
		
		// The factory is done with setting all parameters, now return the estimated utility space
		return additiveUtilitySpaceFactory.getUtilitySpace();
	}

		@Override
		public double getUtility(Bid bid) 
		{ 
			double totalUtil = 0.0;
			if(bid == null) {
            	totalUtil = 0.001;
			} 
			else {
				Map<String, Double> issueValCounts = issueVals(); 
				Object[] attributeWeights = Attributeweightcalc().values().toArray();
				for(int i=0; i<bid.getValues().size(); i++) {	
	        		for(int ii=0; ii<issueValCounts.size(); ii++) {
	        			if(bid.getValues().values().toArray()[i].toString().equals(issueValCounts.keySet().toArray()[ii].toString())){
	        				totalUtil =totalUtil+ ((double)issueValCounts.values().toArray()[ii])*((double)attributeWeights[i]);
	        				break;
	        			}
        	
	        		}
	        	}
			}
			return totalUtil;
			
		}
		
		

	private Map<String, Double> issueVals(){
		
		BidRanking ranking = getUserModel().getBidRanking();
		List<List<String>> issueValues = new ArrayList<List<String>>(ranking.getBidOrder().get(0).getValues().size());
		List<List<Double>> issueValCounts = new ArrayList<List<Double>>(ranking.getBidOrder().get(0).getValues().size());
		for(int j=0; j<ranking.getBidOrder().get(0).getValues().size(); j++) {
			issueValues.add(new ArrayList<String>());
			issueValCounts.add(new ArrayList<Double>());
		}
		for(int i=ranking.getBidOrder().size()-1; i>=0; i--) {

			double max=ranking.getBidOrder().size();
			
			HashMap<Integer, Value> a= ranking.getBidOrder().get(i).getValues();
			
			for(int j=0; j<ranking.getBidOrder().get(i).getValues().size(); j++) {
				Object val = a.values().toArray()[j];
				String v = val.toString();
				if(issueValues.get(j).contains(v)) {
					int index = issueValues.get(j).indexOf(v);
					issueValCounts.get(j).set(index, issueValCounts.get(j).get(index) + i);
				}
				else {
					issueValues.get(j).add(v);
					issueValCounts.get(j).add((double) max);
				}
			}
		}
		
		Map<String, Double> prefList = new HashMap<String, Double>();
		List<String> stringList;
		List<Double> intList ;
		for(int i=0; i < issueValues.size(); i++) {
			// Get two lists of the strings and the counts per issue
			stringList = issueValues.get(i);
			intList = issueValCounts.get(i);

			double max = Collections.max(intList);
			// Normalise the issues
			for(int k=0; k<intList.size(); k++) {
				intList.set(k,intList.get(k)/max);
			}
			// Combine the stringList (issue values) with the intList (issue counts)
			// results in an unsorted list of string,integer pairs
			for(int j=0; j<stringList.size(); j++) {
				prefList.put(stringList.get(j), intList.get(j));
			}
		}
		
		
		return prefList;
	}
	
	
	private Map<Issue, Double> Attributeweightcalc() {
		BidRanking ranking = getUserModel().getBidRanking();
		
		double [] count=new double[ranking.getBidOrder().get(0).getValues().size()];
		for (int l=0;l<ranking.getBidOrder().get(0).getValues().size();l++) {
			List<Boolean> unchange=new ArrayList<Boolean>(Arrays.asList(new Boolean[ranking.getBidOrder().size()]));
			Collections.fill(unchange, Boolean.FALSE);
			
			for(int i=ranking.getBidOrder().size()-1; i>0 ;i--) {
				HashMap<Integer, Value> a= ranking.getBidOrder().get(i).getValues();
				HashMap<Integer, Value> b= ranking.getBidOrder().get(i-1).getValues();
				
				if(a.values().toArray()[l]==(b.values().toArray()[l])) {
					unchange.add(true);
				}
				else {
					unchange.add(false);
					}		
				
			}			
			count[l]= ((double)unchange.stream().filter(p -> p == true).count())/((double)unchange.size());			
		}
		double sum=Arrays.stream(count).sum();
		double[] normal = DoubleStream.of(count).map(p->p/sum).toArray();

		Map<Issue, Double> weightList = new HashMap<Issue, Double>();
		List<Issue> attributeList = getDomain().getIssues();
	
		// Combine the attributeList (issues) with normal (issue weights)
		// results in an unsorted list of string,integer pairs
		for(int i=0; i < normal.length; i++) {
			weightList.put(attributeList.get(i), normal[i]);
			
		}
		
		return weightList;
	}
	@Override
	public String getDescription() 
	{
		return "UncertaintyBOA";
	}

	// All the rest of the agent functionality is defined by the components selected above, using the BOA framework
}