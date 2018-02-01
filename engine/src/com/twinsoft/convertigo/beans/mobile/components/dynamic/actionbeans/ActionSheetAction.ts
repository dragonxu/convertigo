    /**
     * Function ActionSheetAction
     *   
     * @param page  , the current page
     * @param props , the object which holds properties key-value pairs
     * @param vars  , the object which holds variables key-value pairs
     */
    ActionSheetAction(page: C8oPage, props, vars) : Promise<any> {
        return new Promise((resolve, reject)=> {
            const actionSheet : ActionSheet = page.getInstance(ActionSheet);
            let options: any = {};
            const andkey = "androidTheme";
            if(props[andkey] != null){
                options[andkey] = actionSheet.ANDROID_THEMES[props[andkey]];
            }
            delete props[andkey];
            for(let val in props){
                if(props[val] != null){
                        options[val] = props[val];                    
                }
            }
            actionSheet.show(options)
            .then((buttonIndex: number) => {
                page.router.c8o.log.debug("[MB] ActionSheetAction: " + buttonIndex);
                resolve(buttonIndex);
            })
            .catch((err)=>{
                page.router.c8o.log.error("[MB] ActionSheetAction: ", err);
                reject(err);
            })
        });
    }