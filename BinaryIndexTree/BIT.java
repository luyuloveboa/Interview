public class BIT {
    int[] tree;
    int[] ns;
    int n;
    public BIT(int[] nums) {
        n = nums.length;
        tree = new int[n+1];
        ns = new int[n];
        for(int i=0 ; i<n ; i++){
            update(i, nums[i]);
        }
    }
    
    private int getSum(int i){
        i++;
        int sum = 0;
        while(i>0){
            sum += tree[i];
            i -= i & (-i);//find parent
        }
        return sum;
    }

    void update(int i, int val) {
        int diff = val - ns[i];
        ns[i] = val;
        i++;
        while(i<=n){
            tree[i] += diff;
            i += i & (-i);//find next
        }
    }

    public int sumRange(int i, int j) {
        return getSum(j) - getSum(i-1);
    }
    
    public static void main(String args[]){
        int[] input = {1,2,3,4,5,6,7};
        BIT ft = new BIT(input);
        ft.update(4, 46);
        System.out.println(ft.getSum(0));
        System.out.println(ft.getSum(1));
        System.out.println(ft.getSum(2));
        System.out.println(ft.getSum(3));
        System.out.println(ft.getSum(4));
        System.out.println(ft.getSum(5));
        System.out.println(ft.getSum(6));
    }
}