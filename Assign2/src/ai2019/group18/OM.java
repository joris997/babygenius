package ai2019.group18;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Objective;
import genius.core.issue.Value;
import genius.core.utility.*;
import java.util.regex.*;

import java.util.*;

public class OM extends OpponentModel{
    public int a=1;
    private List<Double> weights;
    private HashMap<Integer, HashMap<Value, Integer>> valuesHashMap;
    private HashMap<String, List<Integer>> utilitiesPerValue;
    private Bid previousBid;
    private HashMap<Integer, HashMap<Value, Integer>> personalvaluesHashMap;
    private NegotiationSession negotiationSession;

    private final static double WEIGHT_UPDATE_CONSTANT = 0.2;
    private boolean UsePersonalProfileForEstimation = true;

  
    public OM() {

    }

    public void init(NegotiationSession negSess, Map<String, Double> params) {
        this.negotiationSession = negSess;
        this.weights = new ArrayList<>();
        this.valuesHashMap = new HashMap<>();

        super.init(negSess, params);

        /* Initialize a hashMap that will contain, for every issue, a list of values that the agent has */
        this.utilitiesPerValue = new HashMap<String, List<Integer>>();
        int flag = 0;
        for (Map.Entry<Objective, Evaluator> a : opponentUtilitySpace.getEvaluators()) {
            String valueToString = "";
            try{
                EvaluatorDiscrete value = (EvaluatorDiscrete) a.getValue();
                valueToString += value.toString();
            }
            catch(Exception e){
                EvaluatorInteger value = (EvaluatorInteger) a.getValue();
                valueToString += value.toString();
            }
            /* Initialize a list that contains all issue values for a particular issue */
            List<Integer> utilityList = new ArrayList<>();

            /* Find all values by checking a string on =n.nn (n is number) values */
            Matcher m = Pattern.compile("=\\d+.\\d+").matcher(valueToString);
            while(m.find()){
                /* Isolate the double from the substring */
//            	String tInput = m.group().substring(m.group().indexOf("=")+1, m.group().lastIndexOf(","));
            	String tInput = m.group().substring(m.group().indexOf("=")+1, m.group().length()-1);
            	double input = Double.parseDouble(tInput);
                /* Add the substring to the utility list */
                utilityList.add((int) input);
            }
            /* Add value list to the hashMap. First entry is the string of the Issue. Second entry is the list of all
            values that the agent has for that issue */
            utilitiesPerValue.put(opponentUtilitySpace.getDomain().getIssues().get(flag).toString(), utilityList);
            flag++;
        }
    }

    @Override
    protected void updateModel(Bid bid, double time) {
        int issuesSize = bid.getValues().size();

        /* If empty, Initialise the valuesHashMap for the domain. */
        if (valuesHashMap.size() == 0) {
            previousBid = bid;

            for (Map.Entry<Integer, Value> i : bid.getValues().entrySet()) {
                HashMap<Value, Integer> m = new HashMap<>();

                valuesHashMap.put(i.getKey(), m);
                valuesHashMap.get(i.getKey()).put(i.getValue(), new Integer(1));
            }

            for (int i = 0; i < issuesSize; i++) {
                weights.add(1.0 / issuesSize);
            }
        }
        /* Fill the valuesHashMap for the domain. */
        else {
            for (Map.Entry<Integer, Value> i : bid.getValues().entrySet()) {
                /* Update count for value if it was already contained in an earlier bid. */
                if (valuesHashMap.get(i.getKey()).containsKey(i.getValue())){
                    int count = valuesHashMap.get(i.getKey()).get(i.getValue()) + 1;
                    valuesHashMap.get(i.getKey()).put(i.getValue(), count);
                }
                /* If value not yet encountered, add a new count for it. */
                else {
                    valuesHashMap.get(i.getKey()).put(i.getValue(), new Integer(1));
                }

                /* Update the preliminary weight estimation. */
                Value previous = previousBid.getValue(i.getKey());
                Value current = bid.getValue(i.getKey());

                if (previous == current) {
                    weights.set(i.getKey() - 1, weights.get(i.getKey() - 1) + WEIGHT_UPDATE_CONSTANT);
                }

            }

            previousBid = bid;
        }
    }

    /* Link utilities in the list for all the issues to the counts that have occurred thus far*/
    private void calculateSortedValuesHashMap () {
        personalvaluesHashMap = (HashMap<Integer, HashMap<Value, Integer>>) valuesHashMap.clone();

        for (int i = 0; i < valuesHashMap.size(); i++) {
            HashMap<Value, Integer> issue = valuesHashMap.get(i+1);
            String issueName = negotiationSession.getIssues().get(i).getName();

            /* Sort the counts in the issue from the valuesHashMap */
            ArrayList<Integer> al = new ArrayList<>();
            for (Map.Entry<Value, Integer> entry : issue.entrySet()) {
                al.add(entry.getValue());
            }
            Collections.sort(al);

            /* Get the max, min, secondMax, and secondMin values from the sorted counts list */
            int maxCount = al.get(al.size() - 1);
            int minCount = al.get(0);
            int secondMaxCount = minCount;
            int secondMinCount = maxCount;
            if (al.size() > 1) {
                secondMaxCount = al.get(al.size() - 2);
                secondMinCount = al.get(1);
            }

            List<Integer> correspondingUtilities = utilitiesPerValue.get(issueName);
            Collections.sort(correspondingUtilities);

            /* Get the max, min, secondMax, and secondMin values from the sorted preference profile values list */
            int maxPrefProfile = correspondingUtilities.get(correspondingUtilities.size() - 1);
            int minPrefProfile = correspondingUtilities.get(0);
            int secondMaxPrefProfile = minPrefProfile;
            int secondMinPrefProfile = maxPrefProfile;
            if (correspondingUtilities.size() > 1) {
                secondMaxCount = correspondingUtilities.get(correspondingUtilities.size() - 2);
                secondMinCount = correspondingUtilities.get(1);
            }

            /* Set the ranking of the corresponding values from our preference profile to the ranked opponent count so it results in a similarly distributed issue valuw set to our own */
            for (Map.Entry<Value, Integer> entry : issue.entrySet()) {
                int count = entry.getValue();
                if (personalvaluesHashMap.get(i+1) != null) {
                    if (count == maxCount) {
                        personalvaluesHashMap.get(i+1).put(entry.getKey(), maxPrefProfile);
                    }
                    if (count == minCount) {
                        personalvaluesHashMap.get(i+1).put(entry.getKey(), minPrefProfile);
                    }
                    if (count == secondMaxCount) {
                        personalvaluesHashMap.get(i+1).put(entry.getKey(), secondMaxPrefProfile);
                    }
                    if (count == secondMinCount) {
                        personalvaluesHashMap.get(i+1).put(entry.getKey(), secondMinPrefProfile);
                    }
                }
            }
        }
    }

    /* Function to calculate the normalized value for a value of a particular issue */
    private double getNormalizedIssue (HashMap<Value, Integer> hashMap, Value val){
        int maxValue = 0;
        double result = 0;

        /* Find the maximum value */
        for (Map.Entry<Value, Integer> entry : hashMap.entrySet()) {
            int value = entry.getValue();
            if (maxValue<value) {
                maxValue = value;
            }
        }

        /* Normalize the target value by the maximum value so it is set from 0 to 1 */
        for (Map.Entry<Value, Integer> entry : hashMap.entrySet()) {
            Value mapEntry = entry.getKey();
            if (val.equals(mapEntry)) {
                int value = entry.getValue();
                result = (double)value / (double)maxValue;
            }
        }
        return result;
    }

    /* Normalize the weights of the weights list so the sum of the weights is 1 */
    private List normalizeWeights (List list) {
        double total = 0.0;
        List resList = new ArrayList<>(list);

        /* Get the sum of the weights list*/
        for (Object o : list) {
            total += (double) o;
        }

        /* Divide the weights by the total value to get the weighted value*/
        for (int i = 0; i < list.size(); i++) {
            resList.set(i, ((double)list.get(i) / total));
        }
        return resList;
    }

    /* Estimate the utility of the opponent bid */
    public double getBidEvaluation(Bid bid) {
        double utility = 0.0;
        double normValue;

        if (valuesHashMap.size() != 0) {
            /* Calculate the Utility Function by multiplying the weight and normalized value for an issue */
            List normalizedWeights = this.normalizeWeights(weights);
            for (Map.Entry<Integer, Value> i : bid.getValues().entrySet()) {
                if (UsePersonalProfileForEstimation) {
                    /* When we use our personal issue values spliced on to the counts we use this value below */
                    calculateSortedValuesHashMap();
                    normValue = this.getNormalizedIssue(personalvaluesHashMap.get(i.getKey()), bid.getValue(i.getKey()));
                }
                else {
                    /* When we use the raw value counts use the value below */
                    normValue = this.getNormalizedIssue(valuesHashMap.get(i.getKey()), bid.getValue(i.getKey()));
                }
                /* If normValue is unable to be calculated to a lack of value occurrence we replace it with an arbitrarily low number */
                if(Double.isNaN(normValue)){
                    normValue = 0.001;
                }
                
                double weight = (double)normalizedWeights.get(i.getKey() - 1);
                utility += (weight * normValue);
            }
        }
        return utility;
    }

    @Override
    public AbstractUtilitySpace getOpponentUtilitySpace() {
        return super.getOpponentUtilitySpace();
    }

    public String getName() {
        return "AI-(19-20)-Group18-OpponentModel";
    }
}
