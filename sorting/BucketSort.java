
import java.util.*;

public class BucketSort {
	public static void main(String[] args){
		int[] test = new int[]{7, 5, 2, 4, 5, 3, 9};
		//bucketSort(test);
		bucketSort2(test, 7);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	public static void bucketSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int min = nums[0];
		int max = min;
		int n = nums.length;
		
		for(int i=0 ; i<n ; i++){
			max = Math.max(max, nums[i]);
			min = Math.min(min, nums[i]);
		}
		
		int bucket[] = new int[max-min+1];
		for(int i=0 ; i<n ; i++){
			bucket[nums[i]-min]++;
		}
		
		int i=0;
		int len = bucket.length;
		for(int j=0 ; j<len ; j++){
			for(int k=0 ; k<bucket[j] ; k++){
				nums[i++] = j+min;
			}
		}
	}
	
	public static void bucketSort2(int[] nums, int numOfBucket){
		if(numOfBucket <= 0){
			throw new IllegalArgumentException("Invalid number of buckets");
		}
		if(nums==null || nums.length==0) return;
		
		int min = nums[0];
		int max = min;
		int len = nums.length;
		
		for(int i=0 ; i<len ; i++){
			max = Math.max(max, nums[i]);
			min = Math.min(min, nums[i]);
		}
		
		double interval = (double)(max-min+1)/numOfBucket;
		ArrayList<Integer>[] buckets = new ArrayList[numOfBucket];
		for(int i=0 ; i<numOfBucket ; i++){
			buckets[i] = new ArrayList<Integer>();
		}
		
		for(int i=0 ; i<len ; i++){
			buckets[(int)((nums[i]-min)/interval)].add(nums[i]);
		}
		
		int index = 0;
		for(int i=0 ; i<numOfBucket ; i++){
			Collections.sort(buckets[i]);
			int size = buckets[i].size();
			for(int j=0 ; j<size ; j++){
				nums[index++] = buckets[i].get(j);
			}
		}
	}
}
