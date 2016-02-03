public class Bucket {
    public long key;
    public Thread value;
    //this boolean value is to decide if the bucket
    //has key and value
    //which will be used in deletion
    public boolean live;
    
    /**
     * constructor for a live bucket
     */
    public Bucket(){
    	this.live = true;
    }
    
    /**
     * constructor for a bucket with key and value
     * @param p
     */
    public Bucket(Thread p){
    	this.key = p.getId();
    	this.value = p;
    	this.live = true;
    }
    
    /**
     * get the key of a bucket
     * @return key of a bucket
     */
    public long getKey(){
    	return this.key;
    }
    
    /**
     * get the value of a bucket
     * @return value of a bucket
     */
    public Thread getValue(){
    	return this.value;
    }
	
    /**
     * check to see if the bucket is live
     * @return true if bucket is live and false otherwise
     */
    public boolean getLive(){
    	return this.live;
    }
    
    /**
     * set bucket to live or dead
     * @param b
     */
    public void setLive(boolean b){
    	this.live = b;
    }
    
    /**
     * check to see if two buckets are the same based on the name and the live boolean
     * @param b
     * @return true if buckets are the same
     */
    public boolean isSame(Bucket b){
    	if(b==null){
    		return false;
    	}
    	if(this.value.getName().equals(b.getValue().getName()) && this.live==b.getLive()){
    		return true;
    	}else{
    		return false;
    	}
    }
    
    /**
     * check to see if two buckets are the same based on the key and the value
     * @param b
     * @return true if buckets are the same
     */
    public boolean equals(Bucket b){
    	if(b==null){
    		return false;
    	}
    	if(this.key==b.getKey() && this.value.equals(b.getValue()) && this.live==b.getLive()){
    		return true;
    	}else{
    		return false;
    	}
    }
}
