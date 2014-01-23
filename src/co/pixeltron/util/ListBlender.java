package co.pixeltron.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class ListBlender {
	
	private static boolean debug;

	private ListBlender() {
	}
	
	/**
	 * Takes a list of lists of sorted objects and blends them together based on input 
	 * target percentages, where each element from a given list appears in the final 
	 * list sorted relatively to other elements from that list.
	 * 
	 * @param lists the list of lists
	 * @param blendPercentages blend percentages for each list, in the same order as the list of lists
	 * @param resultSize the size of the blended list
	 * @return
	 */
	public static List<Object> blendLists(List<List<Object>> lists, List<Integer> blendPercentages, int resultSize) {
		
		List<Object> blendedList = new ArrayList<Object>();
		
		// Get the number of elements for each list that we want in the final version
		List<Integer> sizes = getSizes(lists, blendPercentages, resultSize);
		
		// Blend the lists together
		// This portion uses a "round-robin" to add to the final list,
		// but you could replace this loop if you want a different approach
		// to the final list order.
		Map<Integer, Boolean> finishedLists = new HashMap<Integer, Boolean>();
		
		while (finishedLists.size() != lists.size()) {
			for (int i=0;i<lists.size();i++) {
				if (sizes.get(i) != 0) {
					List<Object> currentList = lists.get(i);
					blendedList.add(currentList.get(0));
					currentList.remove(0);
					sizes.set(i, sizes.get(i) - 1);
				} else {
					finishedLists.put(i, true);
				}
			}
		}
		
		return blendedList;
	}
	
	/**
	 * Takes in the list of lists and uses it to generate how many items from each list
	 * to include in the final list based on the percentages and result size
	 * 
	 * @param lists
	 * @param blendPercentages
	 * @param resultSize
	 * @return a list of integers that represent the number of elements from each list to use, in the order
	 * 		of the lists
	 */
	private static List<Integer> getSizes(List<List<Object>> lists, List<Integer> blendPercentages, int resultSize) {
		List<List<Integer>> listValues = new ArrayList<List<Integer>>();
		
		// Setup all the information needed later
		int totalSize = 0;
		for (int i=0;i<lists.size();i++) {
			List<Integer> values = new ArrayList<Integer>();
			values.add(i);
			int size = lists.get(i).size();
			int target = Math.round(Float.valueOf(resultSize * (blendPercentages.get(i) / 100f)));
			values.add(size);
			totalSize += size;
			values.add(target);
			values.add(size - target);
			values.add(size - target);
			listValues.add(values);
		}
		
		List<Integer> targetResults = new ArrayList<Integer>();
		// If we don't have enough total elements, we're going to
		// use them all so we can just return here
		if (totalSize <= resultSize) {
			for (int i=0;i<lists.size();i++) {
				targetResults.add(listValues.get(i).size());
			}
		} else {
			// Sort the lists based on which lists can't meet
			// their target blend percentage
			listValues = sortValues(listValues, 4);
			
			// Adjust the lists until all lists can meet their blend
			// percentages.
			while (getMatrixSize(listValues) < resultSize) {
				listValues = adjustValues(listValues, resultSize);
				if (ListBlender.debug) {
					printTargets(listValues);
				}
				listValues = sortValues(listValues, 4);
			}
			
			// Sort the lists back into the original order and prepare
			// the return values
			listValues = sortValues(listValues, 0);
			for (int i=0;i<listValues.size();i++) {
				List<Integer> currentList = listValues.get(i);
				targetResults.add(currentList.get(1) - currentList.get(4));
			}
		}
		
		if (ListBlender.debug) {
			printTargets(listValues);
		}
		
		return targetResults;
	}
	
	/**
	 * Sort a list of lists based on a given key within those lists. It assumes all lists
	 * are the same size and the key is a valid index for the lists
	 * 
	 * @param values
	 * @param key
	 * @return
	 */
	private static List<List<Integer>> sortValues(List<List<Integer>> values, int key) {
		Map<Integer, Integer> sortedScores = new TreeMap<Integer, Integer>();
		HashMap<Integer, List<Integer>> listMap = new HashMap<Integer, List<Integer>>();
		
		for (int i=0;i<values.size();i++) {
			sortedScores.put(i, values.get(i).get(key));
			listMap.put(i, values.get(i));
		}
		
		sortedScores = MapUtil.sortByValue(sortedScores);	
		
		List<List<Integer>> newValues = new ArrayList<List<Integer>>();
		for (Entry<Integer, Integer> entry : sortedScores.entrySet()) {
			newValues.add(listMap.get(entry.getKey()));
		}
		
		if (ListBlender.debug) {
			printTargets(newValues);
		}
		
		return newValues;
	}
	
	/**
	 * Takes the data matrix from the getSizes function and backfills the list
	 * most in deficit of it's goal by re-allocating its target slots to the other
	 * lists in their relative proportion
	 * 
	 * @param values
	 * @return an adjusted data matrix used in the getSizes function
	 */
	private static List<List<Integer>> adjustValues(List<List<Integer>> values, int targetSize) {
		List<Integer> baseList = values.get(0);
		
		int currentSize = 0;
		int remainder = 0;
		for (int i=0;i<values.size();i++) {
			currentSize += values.get(i).get(1) - Math.abs(values.get(i).get(4));
			if (i > 0 && values.get(i).get(4) > 0) {
				remainder += values.get(i).get(2);
			}
		}
		
		int leftover = 0;
		if (baseList.get(4) < 0) {
			leftover = Math.abs(baseList.get(4));
		} else if (currentSize < targetSize) {
			leftover = targetSize - currentSize;
		}
		
		int i = 1;
		int added = 0;
		while (i < values.size() && added < leftover) {
			List<Integer> currentList = values.get(i);
			if (currentList.get(4) > 0) {
				int allotment = 0;
				Integer goal = currentList.get(2);
				if (goal == 0 && remainder == 0) {
					remainder = values.size() - i;
					goal = 1;
				}
				float share = Float.valueOf(goal) / remainder * leftover;
				if (leftover > 1) {
					allotment = Math.round(share);
				} else {
					allotment = (int) Math.ceil(share + 0.5f);
				}
				if (allotment > currentList.get(4)) {
					allotment = currentList.get(4);
					remainder -= currentList.get(2);
				}
				added += allotment;
				if (baseList.get(4) < 0) {
					baseList.set(4, baseList.get(4) + allotment);
				}
				currentList.set(4, currentList.get(4) - allotment);
				values.set(i, currentList);
				if (ListBlender.debug) {
					printTargets(values);
				}
			}
			i++;
		}
		
		values.set(0, baseList);
		
		return values;
	}
	
	/**
	 * Gets the size of the elements that will be put into the final list
	 * 
	 * @param values
	 * @return
	 */
	private static int getMatrixSize(List<List<Integer>> values) {
		int currentSize = 0;
		for (int i=0;i<values.size();i++) {
			currentSize += values.get(i).get(1) - Math.abs(values.get(i).get(4));
		}
		
		return currentSize;
	}
	
	/**
	 * Prints the data matrix used in the getSizes function
	 * 
	 * @param targetMatrix
	 */
	private static void printTargets(List<List<Integer>> targetMatrix) {
		StringBuilder firstRow = new StringBuilder();
		StringBuilder secondRow = new StringBuilder();
		StringBuilder thirdRow = new StringBuilder();
		StringBuilder fourthRow = new StringBuilder();
		StringBuilder fifthRow = new StringBuilder();
		
		firstRow.append("ID:").append("\t");
		secondRow.append("SIZE:").append("\t");
		thirdRow.append("GOAL:").append("\t");
		fourthRow.append("DIFF:").append("\t");
		fifthRow.append("USE:").append("\t");
		
		for (int i=0;i<targetMatrix.size();i++) {
			firstRow.append(targetMatrix.get(i).get(0)).append("\t");
			secondRow.append(targetMatrix.get(i).get(1)).append("\t");
			thirdRow.append(targetMatrix.get(i).get(2)).append("\t");
			fourthRow.append(targetMatrix.get(i).get(3)).append("\t");
			fifthRow.append(targetMatrix.get(i).get(4)).append("\t");
		}
		
		System.out.println("\nTARGETS:");
		System.out.println(firstRow.toString());
		System.out.println(secondRow.toString());
		System.out.println(thirdRow.toString());
		System.out.println(fourthRow.toString());
		System.out.println(fifthRow.toString());
	}
	
	public static boolean isDebug() {
		return ListBlender.debug;
	}

	public static void setDebug(boolean debug) {
		ListBlender.debug = debug;
	}

	/**
	 * Class used to sort a map by value instead of key
	 * From http://stackoverflow.com/a/2581754/423599
	 * 
	 * @author jocline
	 */
	private static class MapUtil {
		
	    public static <K, V extends Comparable<? super V>> Map<K, V> 
	        sortByValue( Map<K, V> map )
	    {
	        List<Map.Entry<K, V>> list =
	            new LinkedList<Map.Entry<K, V>>( map.entrySet() );
	        Collections.sort( list, new Comparator<Map.Entry<K, V>>()
	        {
	            public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
	            {
	                return (o1.getValue()).compareTo( o2.getValue() );
	            }
	        } );

	        Map<K, V> result = new LinkedHashMap<K, V>();
	        for (Map.Entry<K, V> entry : list)
	        {
	            result.put( entry.getKey(), entry.getValue() );
	        }
	        return result;
	    }
	}
}
