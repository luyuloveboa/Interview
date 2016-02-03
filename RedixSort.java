
public class RedixSort {
	public static void main(String[] args){
		int[] test = new int[]{17, 235, 4212, 34, 5, 753, 4329};
		redixSort(test);
		for(int i: test){
			System.out.print(i + " ");
		}
	}
	
	public static void redixSort(int[] nums){
		if(nums==null || nums.length==0) return;
		
		int len = nums.length;
		int[] store = new int[len];
		int exp = 1;
		int max = nums[0];
		for(int i=0 ; i<len ; i++) max = Math.max(max, nums[i]);
		
		while(max/exp>0){
			int[] count = new int[10];
			
			//store the frequency of each digits in a 0-9 array
			for(int i=0 ; i<len ; i++){
				count[(nums[i]/exp)%10]++;
			}
			
			//circulate the accumulation in count array
			for(int i=1 ; i<10 ; i++){
				count[i] += count[i-1];
			}
			
			//put the elements in nums to store array by getting 
			//the frequency-1 index from digits array 
			for(int i=len-1 ; i>=0 ; i--){
				store[--count[(nums[i]/exp)%10]] = nums[i];
			}
			
			//put all the elements in store back to nums
			for(int i=0 ; i<len ; i++){
				nums[i] = store[i];
			}
			exp *= 10;
		}
	}
}
