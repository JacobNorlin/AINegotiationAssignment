package ai_negotiation;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import negotiator.Bid;
import negotiator.bidding.BidDetails;
import negotiator.boaframework.NegotiationSession;
import negotiator.boaframework.OpponentModel;
import negotiator.issue.Issue;
import negotiator.issue.Objective;
import negotiator.issue.Value;
import negotiator.issue.ValueDiscrete;
import negotiator.issue.IssueDiscrete;
import negotiator.utility.Evaluator;
import negotiator.utility.EvaluatorDiscrete;
import negotiator.utility.UtilitySpace;

/**
 * BOA framework implementation of a simple Frequency model. Based on
 * the hardheaded frequency model. Takes repeated bids and repeated
 * best values into account when updating opponent weights.
 * 
 * @Author Jacob
 * 
 */
public class Group9_OM extends OpponentModel {

	private double standardAddedWeight; //added to issue weight each update
	private int numberOfIssues;
	private int standardValueAddition; //added to value weight each update

	//How much important repeated values are
	private double valueImportanceMultiplier;
	//How importand repeated issues are
	private double issueImportanceMultiplier;
	private double timeOrNot;
	//	private HashMap<Double, Double> opponentBidUtilityHistory;

	//Contains the best values for each issue
	private HashMap<Issue, Value> bestValues;


	/**
	 * Initializes the utility space of the opponent such that all value
	 * issue weights are equal.
	 */
	@Override
	public void init(NegotiationSession negotiationSession, HashMap<String, Double> parameters) throws Exception {

		//		opponentBidUtilityHistory = new HashMap<Double, Double>();
		bestValues = new HashMap<Issue, Value>();

		this.negotiationSession = negotiationSession;
		if (parameters != null && parameters.get("l") != null) {
			standardAddedWeight = parameters.get("l");
		} else {
			standardAddedWeight = 0.2;
		}

		if (parameters != null && parameters.get("vi") != null) {
			valueImportanceMultiplier = parameters.get("vi");
		} else {
			valueImportanceMultiplier = 2;
		}
		if (parameters != null && parameters.get("ii") != null) {
			issueImportanceMultiplier = parameters.get("ii");
		} else {
			issueImportanceMultiplier = 2;
		}
		if (parameters != null && parameters.get("ton") != null) {
			timeOrNot = parameters.get("ton");
		} else {
			timeOrNot = 0;
		}

		standardValueAddition = 1;
		initializeModel();
	}

	/**
	 * Initializes the opponent model. Sets all of the initial issue
	 * weights to be normalized. Also initializes the value weights.
	 */
	private void initializeModel(){
		opponentUtilitySpace = new UtilitySpace(negotiationSession.getUtilitySpace());
		numberOfIssues = opponentUtilitySpace.getDomain().getIssues().size();
		double commonWeight = 1D / (double)numberOfIssues;    

		// initialize the weights
		for(Entry<Objective, Evaluator> e: opponentUtilitySpace.getEvaluators()){
			// set the issue weights
			opponentUtilitySpace.unlock(e.getKey());
			e.getValue().setWeight(commonWeight);
			try {
				// set all value weights to one (they are normalized when calculating the utility)
				for(ValueDiscrete vd : ((IssueDiscrete)e.getKey()).getValues())
					((EvaluatorDiscrete)e.getValue()).setEvaluation(vd,1);  
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}


	/**
	 * A method to check which issues have changed between two bids. If a value
	 * is different then the corresponding issue is set to true.
	 * @param firstBid
	 * @param secondBid
	 * @return HashMap containing the differences
	 */
	private HashMap<Integer, Boolean> bidDifference(BidDetails firstBid, BidDetails secondBid){
		HashMap<Integer, Boolean >issueChanged = new HashMap<Integer, Boolean>();

		for(Issue issue : opponentUtilitySpace.getDomain().getIssues()){
			try{
				Value firstBidValue = firstBid.getBid().getValue(issue.getNumber());
				Value secondBidValue = firstBid.getBid().getValue(issue.getNumber());
				if(firstBidValue.equals(secondBidValue))
					issueChanged.put(issue.getNumber(), false);
				else
					issueChanged.put(issue.getNumber(), true);
			}catch(Exception  e){
				e.printStackTrace();
			}

		}
		return issueChanged;
	}

	/**
	 * Updates the opponent model given a bid and time.
	 */
	@Override
	public void updateModel(Bid opponentBid, double time) {

		//We assume that the first bid an opponent offers is the one with the highes values for every issue
		if(negotiationSession.getOpponentBidHistory().getHistory().size() == 1){
			BidDetails bid = negotiationSession.getOpponentBidHistory().getHistory().get(0);
			for(Issue issue : opponentUtilitySpace.getDomain().getIssues()){
				try {
					bestValues.put(issue, bid.getBid().getValue(issue.getNumber()));
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		}

		if(negotiationSession.getOpponentBidHistory().size() < 2) {
			return;
		}

		int indexOfLastBid = negotiationSession.getOpponentBidHistory().getHistory().size()-1;

		BidDetails lastOppBid = negotiationSession.getOpponentBidHistory().getHistory().get(indexOfLastBid); 
		BidDetails prevOppBid = negotiationSession.getOpponentBidHistory().getHistory().get(indexOfLastBid-1);

		HashMap<Integer, Boolean> differenceBetweenBids = bidDifference(lastOppBid, prevOppBid);

		int numberOfUnchangedIssues = 0;
		for(Integer issue : differenceBetweenBids.keySet()){
			if(!differenceBetweenBids.get(issue))
				numberOfUnchangedIssues++;
		}



		//Now update the weight in the opponent utility space

		/*
		 * The later an issue is changed the more important it should be.
		 * If two issues are changed simultaneously they must be regarded
		 * as equally important. Off by default.
		 */
		double addedWeight;
		if(timeOrNot == 1)
			addedWeight = standardAddedWeight / (double)(time*numberOfIssues);
		else
			addedWeight = standardAddedWeight / (double)(numberOfIssues);

		//normalized weight+added weight
		double totalWeight = 1+addedWeight*(double)numberOfUnchangedIssues; 
		double maximumWeight = 1D - ((double)numberOfIssues) * addedWeight / totalWeight; 


		//Normalize the issue weights
		for(Integer issue : differenceBetweenBids.keySet()){
			Objective currentObjective = opponentUtilitySpace.getObjective(issue);
			double currentWeight = opponentUtilitySpace.getWeight(issue);
			if((!differenceBetweenBids.get(issue)) && currentWeight < maximumWeight){
				//If the value is the best value then we assume the issue is more important
				try {
					if(opponentBid.getValue(issue).equals(bestValues.get(issue))){
						addedWeight*=valueImportanceMultiplier;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				opponentUtilitySpace.setWeight(currentObjective, (currentWeight+addedWeight)/totalWeight);

			}else{
				//If the value is the best value then we assume the issue is more important
				try {
					if(opponentBid.getValue(issue).equals(bestValues.get(issue))){

					}else
						addedWeight = 0;
				} catch (Exception e) {
					e.printStackTrace();
				}
				opponentUtilitySpace.setWeight(currentObjective, (currentWeight+addedWeight)/totalWeight);
			}

		}


		//Reweigh the values based on new data
		try{
			for(Entry<Objective, Evaluator> evaluator: opponentUtilitySpace.getEvaluators()){

				EvaluatorDiscrete evaluatorValue = (EvaluatorDiscrete) evaluator.getValue();

				Bid bid = lastOppBid.getBid();
				IssueDiscrete key = (IssueDiscrete)evaluator.getKey();
				int valueAddition = standardValueAddition;

				//If the bid has been offered before we assume that these values are more important
				if(negotiationSession.getOpponentBidHistory().getHistory().contains(opponentBid)){
					valueAddition*=issueImportanceMultiplier;
				}
				evaluatorValue.setEvaluation(bid.getValue(key.getNumber()), 
						( valueAddition + (evaluatorValue).getEvaluationNotNormalized(((ValueDiscrete)bid.getValue(key.getNumber()) ) ))
						);
			}
		} catch(Exception ex){
			ex.printStackTrace();
		}
	}


	@Override
	public double getBidEvaluation(Bid bid) {
		double result = 0;
		try {
			result = opponentUtilitySpace.getUtility(bid);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public String getName() {
		return "Group9_OM";
	}
}